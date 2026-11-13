package com.tkisor.nekojs.core.state;

import com.tkisor.nekojs.api.ScriptType;

import java.util.ArrayList;
import java.util.List;

/**
 * Holds the global/shared views and the candidate write set for one generation.
 *
 * <p>Each Context (candidate or active) creates one. {@link #globalView()} and
 * {@link #sharedView()} are bound as that Context's {@code global}/{@code shared} members. The
 * view itself is separate from the binding definition.
 *
 * <ul>
 *   <li><b>Non-transactional generation</b> (initial load, STARTUP reset+load, single-file FILE
 *       path): a top-level set/delete/clear commits straight into the store;</li>
 *   <li><b>Transactional generation</b> (candidate): top-level writes go into the write set with
 *       read-your-writes. {@link #preflightJoint()} runs the joint preflight (private write set,
 *       shared write set and external {@link CandidateStatePlan} instances);
 *       {@link #publishJoint()} publishes all of them at once at the commit point;
 *       {@link #discard()} drops every write set when the candidate fails;</li>
 *   <li><b>Generation lifetime</b>: {@link #close()} invalidates only the guest values this
 *       generation wrote. Guest functions and Values of a destroyed Context are not kept alive
 *       forever just because they sit in the map. Non-guest values and the store survive reload
 *       as a whole and are released only when the root closes.</li>
 * </ul>
 */
public final class GenerationGlobals implements AutoCloseable {

    private final GlobalStateStores stores;
    private final ScriptType scriptType;
    private final boolean transactional;
    private final GlobalStore privateStore;
    private final GlobalStore sharedStoreRef;
    private final GlobalStore.MapWriteSet privateWrites;
    private final GlobalStore.MapWriteSet sharedWrites;

    /** External joint plans of the candidate (test plans and later domain plans). Only the owner thread
     * building the candidate writes this list. */
    private final List<CandidateStatePlan> plans = new ArrayList<>();

    /**
     * UI roots bound to this generation. Only the owner thread reads and writes the list. Candidate
     * roots are registered while the candidate builds and become production roots when that
     * candidate commits; closing this generation releases all of them.
     */
    private final List<UiRoot> uiRoots = new ArrayList<>();

    private GlobalView globalView;
    private GlobalView sharedView;
    private volatile boolean closed;
    private boolean published;

    GenerationGlobals(GlobalStateStores stores, ScriptType scriptType, boolean transactional) {
        this(stores, scriptType, transactional, stores.storeFor(scriptType));
    }

    GenerationGlobals(GlobalStateStores stores, ScriptType scriptType, boolean transactional, GlobalStore privateStore) {
        this.stores = stores;
        this.scriptType = scriptType;
        this.transactional = transactional;
        this.privateStore = privateStore;
        this.sharedStoreRef = stores.sharedStore();
        this.privateWrites = transactional ? new GlobalStore.MapWriteSet(privateStore) : null;
        this.sharedWrites = transactional ? new GlobalStore.MapWriteSet(sharedStoreRef) : null;
    }

    public ScriptType scriptType() {
        return scriptType;
    }

    public boolean isTransactional() {
        return transactional && !published;
    }

    /** The type-private {@code global} view of this generation, bound to its Context. */
    public GlobalView globalView() {
        if (globalView == null) {
            globalView = new GlobalView(this, privateStore, privateWrites);
        }
        return globalView;
    }

    /** The explicitly shared view of this generation. The working name is {@code shared}; the view is
     * bound to its Context. */
    public GlobalView sharedView() {
        if (sharedView == null) {
            sharedView = new GlobalView(this, sharedStoreRef, sharedWrites);
        }
        return sharedView;
    }

    // ---- Candidate external plans ----

    /**
     * Joins a candidate plan while the candidate is running. Calling this on a generation that
     * is not a candidate is rejected with an explicit exception. {@code ScriptManager} supplies
     * the Context-side entry point through {@code registerCandidatePlan(Context, plan)}.
     */
    public void addPlan(CandidateStatePlan plan) {
        if (plan == null) throw new NullPointerException("plan");
        if (!isTransactional()) {
            throw new IllegalStateException(
                    "generation of " + scriptType.name + " is not a candidate; plans join candidates only");
        }
        if (closed) {
            throw new IllegalStateException("generation already closed");
        }
        plans.add(plan);
    }

    // ---- Joint preflight, joint publish and discard (transactional generations) ----

    /**
     * Runs the joint preflight in the STATE_PLAN phase: it validates the private and shared write
     * sets for conflicts, then preflights the external plans. Throwing a
     * {@link GlobalStateException} (structured domain) fails the candidate.
     *
     * <p>Write-set validation runs while holding the root lock, because validate reads the
     * store's version/epoch accounting and racing a concurrent writer's bump would violate the
     * JMM; the recheck under the lock at the commit point is the authoritative fallback, but
     * preflight itself must not read without the lock. External plan preflight stays outside the
     * lock, because arbitrary code must not run while the lock is held.
     */
    public void preflightJoint() {
        if (!isTransactional()) return;
        synchronized (stores.lock) {
            if (privateWrites != null) privateWrites.validate();
            if (sharedWrites != null) sharedWrites.validate();
        }
        for (CandidateStatePlan plan : List.copyOf(plans)) {
            try {
                plan.preflight();
            } catch (GlobalStateException e) {
                throw e;
            } catch (Throwable t) {
                throw new GlobalStateException("state-plan-preflight:" + plan.domain(),
                        "candidate state plan '" + plan.domain() + "' failed joint preflight", t);
            }
        }
    }

    /**
     * Publishes jointly at the commit point. No commit-time change may have happened yet. Under
     * the lock it first revalidates both write sets; a failed recheck means another writer
     * committed between preflight and commit, so it throws before any state changes and the
     * active state is fully preserved. It then publishes the external plans (they must not throw
     * once preflight passed; if one does, the global/shared write sets are not published), and
     * finally applies the private and shared write sets in one step while still holding the same
     * lock, so there is no half commit.
     */
    public void publishJoint() {
        if (!isTransactional()) return;
        synchronized (stores.lock) {
            stores.assertOpen();
            // (a) Recheck before any change: a conflict throws here and leads to discardCandidate,
            // so committed writes are never lost
            if (privateWrites != null) privateWrites.validate();
            if (sharedWrites != null) sharedWrites.validate();
            // (b) Publish the external plans before the global/shared write sets (see CandidateStatePlan)
            for (CandidateStatePlan plan : List.copyOf(plans)) {
                try {
                    plan.publish();
                } catch (Throwable t) {
                    throw new GlobalStateException("state-plan-publish:" + plan.domain(),
                            "candidate state plan '" + plan.domain() + "' violated its publish contract"
                                    + " (must not throw after preflight); global/shared write sets not published,"
                                    + " plan's own partial effects are its responsibility", t);
                }
            }
            // (c) Apply both write sets at once under the lock; no failure path remains after this point
            if (privateWrites != null) privateWrites.apply(this);
            if (sharedWrites != null) sharedWrites.apply(this);
            published = true;
        }
    }

    /** Validate all participants before publishing any participant or external plan. */
    public static void preflightBatch(List<GenerationGlobals> participants) {
        if (participants.isEmpty()) return;
        var owner = participants.getFirst().stores;
        synchronized (owner.lock) {
            validateBatch(participants, owner);
        }
        participants.forEach(GenerationGlobals::preflightJoint);
    }

    /** One root lock protects joint publication, including conflicts between candidates. */
    public static void publishBatch(List<GenerationGlobals> participants) {
        if (participants.isEmpty()) return;
        var owner = participants.getFirst().stores;
        synchronized (owner.lock) {
            validateBatch(participants, owner);
            for (var participant : participants) {
                for (var plan : List.copyOf(participant.plans)) {
                    try {
                        plan.publish();
                    } catch (Throwable failure) {
                        throw new GlobalStateException("state-plan-publish:" + plan.domain(),
                                "Candidate plan violated its publish contract; state write sets were not published; "
                                        + "partial domain effects belong to the plan", failure);
                    }
                }
            }
            for (var participant : participants) {
                participant.privateWrites.apply(participant);
                participant.sharedWrites.apply(participant);
                participant.published = true;
            }
        }
    }

    private static void validateBatch(List<GenerationGlobals> participants, GlobalStateStores owner) {
        owner.assertOpen();
        List<GlobalStore.MapWriteSet> writes = new ArrayList<>();
        for (var participant : participants) {
            if (participant.stores != owner || !participant.isTransactional() || participant.closed) {
                throw new GlobalStateException("pack-batch-owner", "Candidate batch has an invalid state owner");
            }
            for (var write : List.of(participant.privateWrites, participant.sharedWrites)) {
                write.validate();
                if (writes.stream().anyMatch(write::overlaps)) {
                    throw new GlobalStateException("pack-batch-write-conflict",
                            "Candidate packages write the same shared state; no package was committed");
                }
                writes.add(write);
            }
        }
    }

    /** Fails or cancels a candidate: discards every write set and plan without touching the committed store. */
    public void discard() {
        synchronized (stores.lock) {
            if (privateWrites != null) privateWrites.discardOps();
            if (sharedWrites != null) sharedWrites.discardOps();
        }
        discardPlans();
        plans.clear();
    }

    /**
     * Releases candidate-scoped plans before their registrations are dropped: a failed or
     * cancelled candidate must not leave pending resources behind. When a single plan fails, the
     * failure is logged and the plan is skipped so one misbehaving plan cannot keep the
     * candidate teardown from completing. Idempotent; publish has already released committed
     * plans.
     */
    private void discardPlans() {
        for (CandidateStatePlan plan : List.copyOf(plans)) {
            try {
                plan.discard();
            } catch (Throwable failure) {
                com.tkisor.nekojs.NekoJS.LOGGER.warn(
                        "[NEKO-1001] Candidate plan discard failed; candidate teardown continues; plan={}",
                        plan.domain(), failure);
            }
        }
    }

    // ---- Generation lifecycle ----

    /**
     * Closes the generation; the Context destruction path calls this. It invalidates the guest
     * values this generation wrote without extending the lifetime of the destroyed Context.
     * Non-guest values stay in the store across reloads. Idempotent.
     */
    @Override
    public void close() {
        if (closed) return;
        closed = true;
        privateStore.evictGuestOwned(this);
        sharedStoreRef.evictGuestOwned(this);
        closeUiRoots("generation-close");
        discardPlans();
        plans.clear();
    }

    public boolean isClosed() {
        return closed;
    }

    // ---- UI root lifecycle ----

    /**
     * Host-side UI root owned by exactly one generation. Registered at creation into the
     * creating generation's globals — the in-flight candidate during a reload, the active
     * generation otherwise — and released exactly once when that generation ends, whatever
     * teardown path ends it (commit supersede, candidate discard, reset, root close).
     */
    public interface UiRoot {

        /**
         * Idempotent host-side release of the Screen, host nodes and input routing owned by
         * this root. Invoked on the generation owner thread; implementations must tolerate
         * repeated invocation and a partially failed earlier invocation.
         *
         * @param reason teardown path identifier used for diagnostics (e.g. {@code generation-close})
         */
        void closeForGeneration(String reason);
    }

    /**
     * Binds a UI root to this generation (owner thread only). Rejected on an already closed
     * generation so a stale script handle fails loudly at creation instead of leaking a Screen
     * that no generation would ever release.
     */
    public void registerUiRoot(UiRoot root) {
        if (root == null) throw new NullPointerException("root");
        if (closed) {
            throw new IllegalStateException(
                    "[NEKO-7001] UI root registration rejected: the owning generation is already closed");
        }
        uiRoots.add(root);
    }

    /** Removes a root that closed itself before its generation ended (owner thread only). */
    public void unregisterUiRoot(UiRoot root) {
        uiRoots.remove(root);
    }

    /**
     * Releases every registered UI root once. Called from {@link #close()} so candidate
     * discard, commit supersede, reset and manager close all route through the same point;
     * {@link #discard()} needs no call of its own because candidate teardown always closes
     * the candidate globals afterwards. Failures of single roots are logged and skipped so
     * one misbehaving root cannot keep the rest of the generation from closing. A root's
     * pending guest-side reconciles need no host-side cancellation here: destroying the
     * generation's guest Context drops them with it, so the host has no independent
     * cancellation mechanism to invoke.
     */
    private void closeUiRoots(String reason) {
        if (uiRoots.isEmpty()) return;
        for (UiRoot root : List.copyOf(uiRoots)) {
            try {
                root.closeForGeneration(reason);
            } catch (Throwable failure) {
                com.tkisor.nekojs.NekoJS.LOGGER.warn(
                        "[NEKO-7005] UI root release failed during '{}'; generation teardown continues",
                        reason, failure);
            }
        }
        uiRoots.clear();
    }
}
