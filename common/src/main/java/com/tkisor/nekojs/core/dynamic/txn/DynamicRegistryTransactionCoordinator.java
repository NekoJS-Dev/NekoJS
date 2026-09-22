package com.tkisor.nekojs.core.dynamic.txn;

import com.tkisor.nekojs.core.dynamic.plan.DynamicAdapterRequest;
import com.tkisor.nekojs.core.dynamic.plan.DynamicCandidateRegistryPlan;
import com.tkisor.nekojs.core.state.GlobalStateException;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.LongSupplier;

/**
 * Server-side owner of the Dynamic Registry batch transaction (ticket 21, AC1/AC2):
 * drives one batch at a time through
 * {@code preflight → same-key fingerprint/conflict detection → server prepare →
 * client prepare/ack → controlled commit}, and turns any failure into a whole-batch
 * abort: the staged plan is discarded and cleaned, the previously activated state
 * keeps serving, and no partial registrations or mixed generations exist anywhere.
 *
 * <p><b>Declaration ledger vs activation</b>: the ticket 16 ledger commit stays where
 * it was (the reload joint commit point / the initial collection publish). This
 * coordinator governs <b>activation</b> — live registry mutation through
 * {@link DynamicRegistryAdapter} and node sync through {@link DynamicSyncTransport}.
 * A batch whose activation aborts remains a committed declaration with a recorded,
 * explicit activation rejection ({@link #activatedGeneration()} does not advance);
 * an identical re-declaration can activate later when sync completes.
 *
 * <p><b>prepare/ack are protocol phases only</b> — nothing here claims cross-process
 * atomic commit. The observable guarantee is: after a COMMIT record, the server
 * adapter has executed the batch exactly once and every participant has received the
 * commit for the same generation; after an ABORT record, no node activated the batch.
 *
 * <p><b>Thread contract</b>: every public method must be called on the transaction
 * owner thread (server main thread in production); the coordinator is not thread-safe
 * by design — cross-thread callers (network handlers) must hop first, per ticket 17's
 * receive-side discipline.
 *
 * <p><b>Defined outcomes, no silent no-ops</b> (AC4): late acks, duplicate acks,
 * unknown-participant acks, disconnects, mid-transaction joins, ack timeout and close
 * preemption each leave an observable record in {@link #phaseLog()} or
 * {@link #syncOutcomes()} and a deterministic state transition.
 */
public final class DynamicRegistryTransactionCoordinator {

    /** Transaction phases in execution order (AC1). */
    public enum Phase { PREFLIGHT, SERVER_PREPARE, CLIENT_PREPARE, ACK, COMMIT }

    /** Lifecycle status of the current/last transaction. */
    public enum Status { IDLE, RUNNING, AWAITING_ACK, COMMITTED, ABORTED }

    /** Observable result of one phase: outcome, generation, owner, domain and error source (AC1). */
    public record PhaseRecord(
            Phase phase, long generation, boolean passed, String owner, String domain,
            String errorSource, String detail) {}

    /** Observable sync outcome of one participant decision (acks, state-sync, joins, leaves). */
    public record SyncOutcome(String participantId, long generation, String outcome, String reason) {}

    /** Terminal summary of the last transaction. */
    public record TransactionSummary(long generation, boolean committed, String abortReason) {}

    private static final int PHASE_LOG_LIMIT = 128;
    private static final int SYNC_OUTCOME_LIMIT = 128;

    /** Owner tag used in phase records produced by the server-side runtime itself. */
    public static final String SERVER_OWNER = "server-activation-runtime";
    /** Failure domain for sync-phase problems (distinct from ledger domains like dynamic-registry-conflict). */
    public static final String SYNC_DOMAIN = "dynamic-registry-sync";

    private final DynamicRegistryAdapter adapter;
    private final DynamicSyncTransport transport;
    private final long ackTimeoutMillis;
    private final LongSupplier clock;

    private record StagedBatch(DynamicCandidateRegistryPlan plan, List<DynamicSyncMessage.Entry> targetState) {}

    private final ArrayDeque<StagedBatch> queue = new ArrayDeque<>();
    private Transaction current;
    private Status status = Status.IDLE;
    private long activatedGeneration = -1L;
    private List<DynamicSyncMessage.Entry> activatedState = List.of();
    private TransactionSummary lastSummary;
    private final List<PhaseRecord> phaseLog = new ArrayList<>();
    private final List<SyncOutcome> syncOutcomes = new ArrayList<>();
    private final Map<String, Long> pendingStateSync = new LinkedHashMap<>();

    private final class Transaction {
        final DynamicCandidateRegistryPlan plan;
        final List<DynamicSyncMessage.Entry> targetState;
        final List<DynamicAdapterRequest> requests;
        final long deadlineMillis;
        final Set<String> awaiting = new LinkedHashSet<>();
        Phase phase = Phase.PREFLIGHT;

        Transaction(StagedBatch staged, long deadlineMillis) {
            this.plan = staged.plan();
            this.targetState = staged.targetState();
            this.requests = staged.plan().adapterRequests();
            this.deadlineMillis = deadlineMillis;
        }

        long generation() {
            return plan.generation();
        }
    }

    public DynamicRegistryTransactionCoordinator(
            DynamicRegistryAdapter adapter, DynamicSyncTransport transport,
            long ackTimeoutMillis, LongSupplier clock) {
        this.adapter = adapter;
        this.transport = transport;
        this.ackTimeoutMillis = ackTimeoutMillis;
        this.clock = clock;
    }

    // ---- staging and pumping ----

    /**
     * Enqueues a committed declaration batch for activation. Does not run any phase —
     * the caller stays free to invoke this from inside a reload commit point; phases
     * run on {@link #pump()} / ack / join / leave / tick events.
     */
    public synchronized void stage(
            DynamicCandidateRegistryPlan plan, List<DynamicSyncMessage.Entry> targetState) {
        queue.add(new StagedBatch(plan, List.copyOf(targetState)));
    }

    /** Batches waiting for their transaction to start. */
    public synchronized int queuedBatchCount() {
        return queue.size();
    }

    /** Starts the next queued transaction when none is in flight. */
    public synchronized void pump() {
        if (current != null || queue.isEmpty()) {
            return;
        }
        beginNext(queue.poll());
    }

    // ---- participant events ----

    /**
     * Participant ack (prepare or state-sync answer). Late, duplicate and
     * unknown-transaction acks get a defined recorded outcome instead of a silent
     * no-op; a reject aborts the whole batch.
     */
    public synchronized void onAck(String participantId, long generation, boolean accepted, String reason) {
        Long pendingSync = pendingStateSync.get(participantId);
        if (pendingSync != null && pendingSync == generation) {
            pendingStateSync.remove(participantId);
            note(new SyncOutcome(participantId, generation,
                    accepted ? "state-sync-completed" : "state-sync-rejected", reason));
            return;
        }
        if (current == null || current.generation() != generation) {
            note(new SyncOutcome(participantId, generation, "late-ack-outside-transaction", reason));
            return;
        }
        if (!current.awaiting.contains(participantId)) {
            note(new SyncOutcome(participantId, generation, "duplicate-ack", reason));
            return;
        }
        note(new SyncOutcome(participantId, generation, accepted ? "ack" : "ack-rejected", reason));
        if (!accepted) {
            record(Phase.ACK, current.generation(), false, participantId, SYNC_DOMAIN,
                    "participant:" + participantId + ":rejected",
                    reason == null || reason.isBlank() ? "participant rejected the prepare" : reason);
            abortCurrent("participant-rejected", participantId);
            return;
        }
        current.awaiting.remove(participantId);
        if (current.awaiting.isEmpty()) {
            record(Phase.ACK, current.generation(), true, SERVER_OWNER, SYNC_DOMAIN, null,
                    "all participants acked");
            commitCurrent();
        }
    }

    /**
     * A participant joined. Mid-transaction joiners receive the in-flight prepare and
     * must ack before commit (no node is left behind by a committing majority);
     * otherwise a joined participant receives a catch-up {@code STATE_SYNC} of the
     * already-activated state (which never activates a newer generation).
     */
    public synchronized void onParticipantJoined(String participantId) {
        if (current != null && status == Status.AWAITING_ACK) {
            current.awaiting.add(participantId);
            transport.send(participantId, DynamicSyncMessage.prepare(current.generation(), current.targetState));
            note(new SyncOutcome(participantId, current.generation(), "joined-mid-transaction",
                    "prepare re-sent; ack required before commit"));
            return;
        }
        if (activatedGeneration >= 0L && !activatedState.isEmpty()) {
            pendingStateSync.put(participantId, activatedGeneration);
            transport.send(participantId, DynamicSyncMessage.stateSync(activatedGeneration, activatedState));
            note(new SyncOutcome(participantId, activatedGeneration, "state-sync-sent", null));
        }
    }

    /**
     * A participant left (disconnect). If it had not acked the in-flight batch, the
     * batch aborts — a node that may hold a diverged prepare must never be exposed to
     * a mixed generation; the next batch retries.
     */
    public synchronized void onParticipantLeft(String participantId) {
        pendingStateSync.remove(participantId);
        if (current != null && current.awaiting.remove(participantId)) {
            record(Phase.ACK, current.generation(), false, participantId, SYNC_DOMAIN,
                    "participant:" + participantId + ":disconnected",
                    "participant disconnected before acking the prepare");
            abortCurrent("participant-disconnected", participantId);
        }
        note(new SyncOutcome(participantId, activatedGeneration, "left", null));
    }

    /**
     * A participant's post-commit activation report — the answer to a COMMIT or catch-up
     * STATE_SYNC it already received (the participant executes its own activation when
     * the commit arrives). A failure cannot un-commit the server batch, so the defined
     * outcome (AC4) is an observable record plus a fresh catch-up STATE_SYNC of the
     * activated state, letting the diverged node converge on delivery instead of
     * silently staying behind. Success reports are recorded only.
     */
    public synchronized void onParticipantActivationReport(
            String participantId, long generation, boolean activated, String detail) {
        if (activated) {
            note(new SyncOutcome(participantId, generation, "activation-report", detail));
            return;
        }
        note(new SyncOutcome(participantId, generation, "activation-failed-post-commit",
                detail == null || detail.isBlank() ? "no detail" : detail));
        if (activatedGeneration >= 0L && !activatedState.isEmpty()) {
            pendingStateSync.put(participantId, activatedGeneration);
            transport.send(participantId, DynamicSyncMessage.stateSync(activatedGeneration, activatedState));
        }
    }

    /** Deadline check for the in-flight ack phase plus queue pump. */
    public synchronized void tick(long nowMillis) {
        if (current != null && status == Status.AWAITING_ACK && nowMillis >= current.deadlineMillis) {
            record(Phase.ACK, current.generation(), false, SERVER_OWNER, SYNC_DOMAIN, "ack-timeout",
                    "no ack from " + current.awaiting + " within " + ackTimeoutMillis + " ms");
            abortCurrent("ack-timeout", String.join(",", current.awaiting));
        }
        pump();
    }

    /**
     * Close preemption (server stopping / root close): the in-flight batch aborts and
     * every queued batch is discarded with a recorded reason — nothing commits across
     * a close boundary (AC2). The queue is drained <b>before</b> the in-flight abort
     * so the abort path's follow-up pump cannot start a queued batch that this close
     * was supposed to discard.
     */
    public synchronized boolean abortInFlight(String cause) {
        boolean aborted = false;
        while (!queue.isEmpty()) {
            StagedBatch discarded = queue.poll();
            record(Phase.PREFLIGHT, discarded.plan().generation(), false, SERVER_OWNER, SYNC_DOMAIN,
                    "close-preemption:" + cause, "queued batch discarded before its transaction started");
            lastSummary = new TransactionSummary(discarded.plan().generation(), false,
                    "close-preemption:" + cause);
        }
        if (current != null) {
            record(current.phase, current.generation(), false, SERVER_OWNER, SYNC_DOMAIN,
                    "close-preemption:" + cause, "close preemption aborts the in-flight batch");
            abortCurrent("close-preemption:" + cause, null);
            aborted = true;
        }
        pendingStateSync.clear();
        return aborted;
    }

    // ---- observation ----

    public synchronized Status status() {
        return status;
    }

    /** Server-side activation watermark: the newest generation whose batch actually activated. */
    public synchronized long activatedGeneration() {
        return activatedGeneration;
    }

    /** Full definition set activated at {@link #activatedGeneration()} (empty when none). */
    public synchronized List<DynamicSyncMessage.Entry> activatedState() {
        return activatedState;
    }

    public synchronized TransactionSummary lastSummary() {
        return lastSummary;
    }

    /** Phase records of all transactions (bounded, oldest evicted). */
    public synchronized List<PhaseRecord> phaseLog() {
        return List.copyOf(phaseLog);
    }

    /** Participant sync outcomes, including every late/duplicate/leave case (bounded). */
    public synchronized List<SyncOutcome> syncOutcomes() {
        return List.copyOf(syncOutcomes);
    }

    /** Ack deadline policy of this coordinator (diagnostics). */
    public long ackTimeoutMillis() {
        return ackTimeoutMillis;
    }

    // ---- internals ----

    private void beginNext(StagedBatch staged) {
        Transaction txn = new Transaction(staged, clock.getAsLong() + ackTimeoutMillis);
        current = txn;
        status = Status.RUNNING;

        // Phase 1: preflight — collection errors and same-key fingerprint conflicts
        // (ticket 16 semantics, re-checked against the possibly-advanced ledger).
        try {
            txn.plan.preflight();
        } catch (GlobalStateException e) {
            record(Phase.PREFLIGHT, txn.generation(), false, SERVER_OWNER, e.domain(),
                    "preflight:" + e.domain(), e.getMessage());
            abortCurrent("preflight:" + e.domain(), null);
            return;
        }
        record(Phase.PREFLIGHT, txn.generation(), true, SERVER_OWNER,
                com.tkisor.nekojs.core.dynamic.plan.DynamicCandidateRegistryPlan.DOMAIN, null,
                txn.requests.size() + " request(s) passed preflight");

        // Phase 2: server prepare — Adapter validation (config gate, types, registries).
        try {
            adapter.prepareActivation(txn.requests);
        } catch (Throwable e) {
            record(Phase.SERVER_PREPARE, txn.generation(), false, SERVER_OWNER, SYNC_DOMAIN,
                    "adapter:prepare", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            abortCurrent("adapter-prepare-rejected", null);
            return;
        }
        record(Phase.SERVER_PREPARE, txn.generation(), true, SERVER_OWNER, SYNC_DOMAIN, null,
                "adapter accepted " + txn.requests.size() + " request(s)");

        // Phase 3: client prepare — full target state to every remote participant.
        List<String> participants = transport.participants();
        if (participants.isEmpty()) {
            record(Phase.CLIENT_PREPARE, txn.generation(), true, SERVER_OWNER, SYNC_DOMAIN, null,
                    "no remote participants; commit proceeds without acks");
            record(Phase.ACK, txn.generation(), true, SERVER_OWNER, SYNC_DOMAIN, null,
                    "no acks required (single-node)");
            commitCurrent();
            return;
        }
        txn.awaiting.addAll(participants);
        txn.phase = Phase.CLIENT_PREPARE;
        record(Phase.CLIENT_PREPARE, txn.generation(), true, SERVER_OWNER, SYNC_DOMAIN, null,
                "prepare sent to " + participants);
        status = Status.AWAITING_ACK;
        txn.phase = Phase.ACK;
        // Set AWAITING_ACK before sending: a synchronous transport (in-JVM cluster,
        // same-thread delivery) may answer the ack inside send() and commit the batch
        // before the loop reaches the remaining participants. The transaction identity
        // check stops the loop the moment this batch is no longer current (committed,
        // aborted, or superseded by the next queued batch's pump).
        for (String participantId : List.copyOf(participants)) {
            transport.send(participantId, DynamicSyncMessage.prepare(txn.generation(), txn.targetState));
            if (current != txn) {
                break;
            }
        }
    }

    private void commitCurrent() {
        long generation = current.generation();
        // Phase 5: commit — the single decision point. Adapter execution happens before
        // the commit broadcast so a degraded activation never masquerades as committed.
        try {
            adapter.activate(current.requests);
        } catch (Throwable e) {
            record(Phase.COMMIT, generation, false, SERVER_OWNER, SYNC_DOMAIN, "adapter:activate",
                    "adapter violated its no-throw contract after prepare; activation is degraded: "
                            + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage())
                            + rollbackAfterDegradedActivation(current.requests));
            abortCurrent("adapter-activate-failed", null);
            return;
        }
        activatedGeneration = generation;
        activatedState = current.targetState;
        transport.broadcast(DynamicSyncMessage.commit(generation));
        record(Phase.COMMIT, generation, true, SERVER_OWNER, SYNC_DOMAIN, null,
                "activated on the server; commit broadcast sent");
        lastSummary = new TransactionSummary(generation, true, null);
        releaseCurrent();
        status = Status.COMMITTED;
        pump();
    }

    /**
     * Demands adapter restoration after a degraded activation (AC2: no partial
     * registration survives); never throws — a failing rollback widens the recorded
     * degradation detail instead of escaping the commit path.
     */
    private String rollbackAfterDegradedActivation(List<DynamicAdapterRequest> requests) {
        try {
            adapter.rollbackActivation(requests);
            return "; adapter rollback restored the previously activated state";
        } catch (Throwable rollbackFailure) {
            return "; adapter rollback ALSO failed: " + (rollbackFailure.getMessage() == null
                    ? rollbackFailure.getClass().getSimpleName() : rollbackFailure.getMessage());
        }
    }

    private void abortCurrent(String reason, String participant) {
        long generation = current.generation();
        transport.broadcast(DynamicSyncMessage.abort(generation, reason));
        lastSummary = new TransactionSummary(generation, false, reason + (participant == null ? "" : ":" + participant));
        releaseCurrent();
        status = Status.ABORTED;
        pump();
    }

    /**
     * Releases the current transaction's resources in ownership order: the staged plan
     * (and its request list / pending-ack set, which are transaction-scoped) become
     * unreachable together — the coordinator's post-commit state keeps only the wire
     * entries of the activated generation, never the plan object. The ledger batch the
     * plan committed needs no further cleanup (its bookkeeping lives in the store).
     */
    private void releaseCurrent() {
        current = null;
    }

    private void record(Phase phase, long generation, boolean passed, String owner,
            String domain, String errorSource, String detail) {
        phaseLog.add(new PhaseRecord(phase, generation, passed, owner, domain, errorSource, detail));
        if (phaseLog.size() > PHASE_LOG_LIMIT) {
            phaseLog.remove(0);
        }
    }

    private void note(SyncOutcome outcome) {
        syncOutcomes.add(outcome);
        if (syncOutcomes.size() > SYNC_OUTCOME_LIMIT) {
            syncOutcomes.remove(0);
        }
    }
}
