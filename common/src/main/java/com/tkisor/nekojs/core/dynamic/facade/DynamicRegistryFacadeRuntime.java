package com.tkisor.nekojs.core.dynamic.facade;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.core.dynamic.plan.DynamicCandidateRegistryPlan;
import com.tkisor.nekojs.core.dynamic.plan.DynamicRegistryPlanStore;
import com.tkisor.nekojs.core.dynamic.txn.DynamicRegistryAdapter;
import com.tkisor.nekojs.core.dynamic.txn.DynamicRegistryTransactionCoordinator;
import com.tkisor.nekojs.core.dynamic.txn.DynamicSyncMessage;
import com.tkisor.nekojs.core.dynamic.txn.DynamicSyncTransport;
import com.tkisor.nekojs.core.state.CandidateStatePlan;
import com.tkisor.nekojs.core.state.GlobalStateException;
import com.tkisor.nekojs.core.lifecycle.CandidateDomainCollector;
import com.tkisor.nekojs.script.ScriptContextRegistry;
import com.tkisor.nekojs.script.ScriptManager;
import graal.graalvm.polyglot.Context;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;

/**
 * Runtime of the dynamic registration facade. It holds this domain's
 * {@link DynamicRegistryPlanStore} and serves two collection entry points:
 * <ul>
 *   <li><b>Initial candidate</b>: when the server registry is ready the platform calls
 *       {@link #collectInitial(String)}, which posts the collection event on the active bus and
 *       publishes the batch only if preflight succeeds; a failure is only recorded and starts no
 *       separate write.</li>
 *   <li><b>Reload candidate collection</b>: it implements {@link CandidateDomainCollector} and the
 *       reload pipeline calls it during the candidate phase (after EVENT_PLAN, before
 *       STATE_PLAN). It collects the candidate listeners' declarations into an inert plan and
 *       attaches that plan together with the other candidate plans; the commit point publishes
 *       them as a unit, and a failed candidate is discarded with the rest.</li>
 * </ul>
 *
 * <p>This class only implements local inert plan behavior. A published plan only updates the
 * claim/stale/exposed ledger and produces an inert {@code DynamicAdapterRequest}. It does not
 * activate multiplayer sync, does not perform a registry mutation and does not claim dynamic hot
 * reload; public activation is decided by the transaction/sync gate. The production instance is
 * held by the platform adapter (NeoForge dynamic package); tests and harnesses may construct
 * isolated instances freely.
 *
 * <p><b>Why plans are inert</b>: this class and the whole
 * {@code com.tkisor.nekojs.core.dynamic.plan} package live in common with zero Minecraft/loader
 * imports (enforced by {@code :common:checkCommonIsolation} and guardLint L1/L2), so "modify the
 * live registry during the candidate phase" cannot be written in this module. A plan only holds
 * immutable definition snapshots and inert {@code DynamicAdapterRequest} descriptions: no platform
 * execution channel, no production callback, no early publishing of external bindings. When a
 * candidate fails the plan object becomes unreachable (no temporary resources need releasing),
 * the ledger is not written, and the old active definitions keep serving. See
 * {@code DynamicPlanInertnessTest} for structural checks on the plan package.
 *
 * <p><b>Activation engine</b>: {@link #bindActivationEngine} optionally attaches a
 * batch-transaction engine, and {@link #pumpActivation()} then hands every ledger-committed batch
 * to it through a pull-based pending queue; the plan itself carries no hook. Unbound, this runtime
 * is exactly the inert local plan runtime. Production binding is not wired yet, because activation
 * stays gated by the transaction/sync gates.
 */
public final class DynamicRegistryFacadeRuntime implements CandidateDomainCollector, AutoCloseable {

    /** Observable result of one collection round, for tests and diagnostics; it carries no internal
     *  state. */
    public record CollectionOutcome(
            String trigger,
            long generation,
            boolean published,
            String failureDomain,
            String failureDetail) {

        static CollectionOutcome success(String trigger, DynamicCandidateRegistryPlan plan) {
            return new CollectionOutcome(trigger, plan.generation(), true, null, null);
        }

        static CollectionOutcome failure(String trigger, DynamicCandidateRegistryPlan plan,
                GlobalStateException error) {
            return new CollectionOutcome(trigger, plan.generation(), false, error.domain(), error.getMessage());
        }
    }

    /**
     * Observable record of one <b>candidate-phase</b> collection. This is the direct observation
     * point for re-collecting a script/data reload during the candidate phase. It is separate from
     * the {@link CollectionOutcome} of the initial collection, because the candidate phase only
     * collects and attaches a plan while the commit point decides whether it is published. It also
     * carries no internal state and holds no plan reference, so a failed candidate's plan becomes
     * unreachable afterwards and no temporary resources are left behind.
     */
    public record CandidateCollectionRecord(
            String note,
            long planGeneration,
            int collectedDefinitions,
            boolean poisoned,
            String failureDetail) {

        /** This domain did not take part in the candidate: not a SERVER type, or unused with an empty
         *  ledger. */
        static CandidateCollectionRecord skipped(String note) {
            return new CandidateCollectionRecord(note, -1L, 0, false, null);
        }

        static CandidateCollectionRecord collected(DynamicCandidateRegistryPlan plan) {
            return new CandidateCollectionRecord("collected", plan.generation(), plan.definitions().size(),
                    plan.hasCollectionErrors(),
                    plan.collectionErrors().isEmpty() ? null : String.join("; ", plan.collectionErrors()));
        }

        /** Whether this domain actually collected and attached a plan during that candidate phase. */
        public boolean participated() {
            return planGeneration >= 0L;
        }
    }

    private final DynamicRegistryPlanStore store = new DynamicRegistryPlanStore();
    private volatile CollectionOutcome lastOutcome;
    private volatile CandidateCollectionRecord lastCandidateCollection;
    private volatile DynamicRegistryTransactionCoordinator activationEngine;
    /**
     * Plans begun by this runtime that are waiting for {@link #pumpActivation()} to observe their
     * ledger commit. Staging is pull-based: the plan itself carries no hook, so the plan surface
     * stays pure inert data with no execution channel. A failed or discarded candidate is dropped
     * at the next pump, and a plan is pure data that holds no resources, so the brief reference is
     * not a cleanup concern.
     */
    private final ArrayDeque<DynamicCandidateRegistryPlan> pendingCandidates = new ArrayDeque<>();
    /** Newest plan generation ever handed to the activation engine. */
    private long lastStagedPlanGeneration;

    /** This domain's plan ledger, as observed from the Registry Runtime side. */
    public DynamicRegistryPlanStore store() {
        return store;
    }

    /**
     * Binds the activation engine. With an engine bound, every committed declaration batch is also
     * staged for the batch transaction: preflight, server prepare, client prepare/ack, then a
     * controlled commit through the adapter and transport. Without one the runtime stays exactly
     * the inert local plan runtime. The platform assembly binds the production engine only when
     * the dynamic registry configuration gate is enabled.
     */
    public synchronized void bindActivationEngine(
            DynamicRegistryAdapter adapter, DynamicSyncTransport transport,
            long ackTimeoutMillis, LongSupplier clock) {
        clearActivationEngine("engine-rebound");
        this.activationEngine = new DynamicRegistryTransactionCoordinator(
                adapter, transport, ackTimeoutMillis, clock);
    }

    /** Drops the activation engine (server stopped); aborts and discards in-flight work first. */
    public synchronized void clearActivationEngine(String cause) {
        if (activationEngine != null) {
            activationEngine.close(cause);
        }
        this.activationEngine = null;
    }

    /** Root-owned domain release; ordinary script generation reload never closes this runtime. */
    @Override
    public void close() {
        clearActivationEngine("runtime-root-closed");
    }

    /** Bound activation engine, or null while unbound (inert local plans only). */
    public DynamicRegistryTransactionCoordinator activationEngine() {
        return activationEngine;
    }

    /**
     * Owner-thread activation pump: hands every ledger-committed batch to the bound engine and
     * starts the next transaction when none is in flight. It is pull-based by design.
     * {@link #collectInitial} calls it after its own commit, and the production wiring (server
     * tick / post-reload event, owner: network phase) calls it for reload-driven commits. It never
     * runs inside a reload's commit, because staging only enqueues and the phases are event-driven.
     */
    public synchronized void pumpActivation() {
        DynamicRegistryTransactionCoordinator engine = activationEngine;
        if (engine == null) {
            return;
        }
        while (!pendingCandidates.isEmpty()) {
            DynamicCandidateRegistryPlan candidate = pendingCandidates.poll();
            if (!candidate.isPublished()) {
                continue; // failed/discarded candidate: its declarations never committed
            }
            if (candidate.generation() <= lastStagedPlanGeneration) {
                continue; // already staged (or superseded by a newer staged batch)
            }
            if (!candidate.adapterRequests().isEmpty() || !store.isEmpty()) {
                engine.stage(candidate, targetState());
            }
            lastStagedPlanGeneration = candidate.generation();
        }
        engine.pump();
    }

    /** Full target state of the ledger as wire entries (full state, never a delta). */
    private List<DynamicSyncMessage.Entry> targetState() {
        List<DynamicSyncMessage.Entry> entries = new ArrayList<>();
        store.exposedSnapshot().values()
                .forEach(entry -> entries.add(
                        new DynamicSyncMessage.Entry(entry.definition(), entry.ownerScriptId())));
        return entries;
    }

    /** Observable result of the latest collection round, for diagnostics and tests; it carries no
     *  internal state. */
    public CollectionOutcome lastOutcome() {
        return lastOutcome;
    }

    /** Observable record of the latest <b>candidate-phase</b> collection, for diagnostics and tests;
     *  null when no candidate-phase collection has run. */
    public CandidateCollectionRecord lastCandidateCollection() {
        return lastCandidateCollection;
    }

    // ---- Initial candidate, triggered when the server registry is ready ----

    /**
     * Runs one collection by posting on the active bus. The bus catches and records listener
     * exceptions; a collection failure reaches preflight through the payload's poison flag, and
     * preflight then rejects the batch. The batch is published only when preflight passes. A
     * failure only returns the outcome and logs it, so it starts no separate write, and the old
     * active definitions, if any, keep serving.
     */
    public CollectionOutcome collectInitial(String trigger) {
        DynamicCandidateRegistryPlan plan = store.beginBatch();
        pendingCandidates.add(plan);
        DynamicRegistryEventJS payload = new DynamicRegistryEventJS(plan);
        DynamicRegistryEvents.DYNAMIC_REGISTRY.post(payload);
        try {
            plan.preflight();
        } catch (GlobalStateException e) {
            NekoJS.LOGGER.error(
                    "DynamicRegistry candidate batch {} (trigger '{}') rejected: [{}] {} — nothing published,"
                            + " previous active definitions (if any) keep serving",
                    plan.generation(), trigger, e.domain(), e.getMessage());
            lastOutcome = CollectionOutcome.failure(trigger, plan, e);
            return lastOutcome;
        }
        plan.publish();
        NekoJS.LOGGER.info(
                "DynamicRegistry candidate batch {} committed (trigger '{}', {} definition(s)); {}",
                plan.generation(), trigger, plan.definitions().size(), activationEngine == null
                        ? "local plan retained without an activation engine"
                        : "activation queued for the transaction/sync gate");
        lastOutcome = CollectionOutcome.success(trigger, plan);
        // Initial collection runs on the owner thread outside any reload commit lock,
        // so the activation transaction may start immediately (zero participants at
        // server start in production; reload-driven staging is pumped by the platform).
        pumpActivation();
        return lastOutcome;
    }

    // ---- Reload candidate collection (CandidateDomainCollector) ----

    @Override
    public String domain() {
        return DynamicCandidateRegistryPlan.DOMAIN;
    }

    /** SERVER-only domain: the collector only takes part in SERVER candidates. CLIENT, STARTUP and
     *  TEST candidates never touch the ledger. */
    @Override
    public ScriptType scriptType() {
        return ScriptType.SERVER;
    }

    /**
     * Collects during the candidate phase, SERVER domain only. It runs every pending listener
     * callback of the candidate that belongs to
     * {@link DynamicRegistryEvents#DYNAMIC_REGISTRY} one by one, on the same owner thread and
     * through {@link Handle#execute} with its scriptId switch, callback depth marker and exception
     * propagation, the same as production dispatch. An exception from one listener is recorded as
     * a collection error that poisons the whole batch, and the remaining listeners still run as
     * the EventBus requires; at STATE_PLAN the batch is rejected with the exact domain. Errors are
     * rethrown with the same semantics as dispatch, where the reload pipeline attributes them to a
     * collector crash as {@code domain-collect:<domain>}, and the interrupt flag is restored.
     *
     * <p>Empty candidate: when the candidate has no listener for this domain, an empty plan is
     * attached only if the ledger already holds exposed definitions, which marks the items that
     * are no longer declared as stale/retired. If the ledger is empty the domain is skipped, so a
     * reload that does not use it takes no part at all.
     */
    @Override
    public void collect(Handle handle) {
        if (handle.scriptType() != ScriptType.SERVER) {
            lastCandidateCollection = CandidateCollectionRecord.skipped("skipped-non-server");
            return; // SERVER-only domain: never touch the ledger because of a reload of another type
        }
        List<com.tkisor.nekojs.api.event.EventBusJS.PendingListener> ofBus =
                handle.listenersOf(DynamicRegistryEvents.DYNAMIC_REGISTRY);
        if (ofBus.isEmpty() && store.isEmpty()) {
            lastCandidateCollection = CandidateCollectionRecord.skipped("skipped-unused-domain");
            return; // This domain has never been used and the candidate declares nothing: attach no empty plan
        }
        DynamicCandidateRegistryPlan plan = store.beginBatch();
        pendingCandidates.add(plan);
        DynamicRegistryEventJS payload = new DynamicRegistryEventJS(plan);
        for (com.tkisor.nekojs.api.event.EventBusJS.PendingListener pending : ofBus) {
            try {
                handle.execute(pending, payload);
            } catch (Throwable e) {
                // The catch shape matches the production dispatch closure (catch(Throwable) in
                // EventBusJS.register*): restore the interrupt flag, rethrow Error to the caller
                // (the reload pipeline attributes it to a collector crash), and record anything else
                // (guest PolyglotException and domain data errors) as a collection error that poisons
                // the batch before continuing with the remaining listeners, so one bad declaration
                // does not hide the other collection errors. A kill is reported the same way as
                // production: when a resource limit terminates the candidate Context, it counts as a
                // candidate failure instead of being swallowed as an ordinary collection error.
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                if (e instanceof Error error) {
                    throw error;
                }
                ScriptManager.reportContextKilled(handle.candidateContext(), e);
                plan.noteCollectionError(pending.scriptId(), e);
                NekoJS.LOGGER.error("DynamicRegistry candidate collection failed in script '{}': {}",
                        pending.scriptId(), e.getMessage());
            }
        }
        lastCandidateCollection = CandidateCollectionRecord.collected(plan);
        handle.registerPlan(plan);
    }
}
