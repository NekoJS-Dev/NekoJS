package com.tkisor.nekojs.core.dynamic.txn;

import com.tkisor.nekojs.core.dynamic.plan.DynamicCandidateRegistryPlan;
import com.tkisor.nekojs.core.dynamic.plan.DynamicDefinition;
import com.tkisor.nekojs.core.dynamic.plan.DynamicRegistryPlanStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Ticket 21, AC1/AC2/AC4/AC6 (server side): batch order preflight → fingerprint/conflict
 * → server prepare → client prepare/ack → controlled commit, with every phase's
 * result/generation/owner/domain/error source observable; any failure aborts the
 * whole batch with the old activated state still serving; late/duplicate/disconnect/
 * timeout/join all have defined outcomes (no silent no-op).
 */
class DynamicRegistryTransactionSemanticsTest {

    private DynamicRegistryPlanStore store;
    private RecordingTxnSupport.RecordingAdapter adapter;
    private RecordingTxnSupport.ScriptedTransport transport;
    private DynamicRegistryTransactionCoordinator coordinator;
    private long clock;

    @BeforeEach
    void setUp() {
        store = new DynamicRegistryPlanStore();
        adapter = new RecordingTxnSupport.RecordingAdapter();
        transport = new RecordingTxnSupport.ScriptedTransport();
        coordinator = new DynamicRegistryTransactionCoordinator(adapter, transport, 10_000L, () -> clock);
        clock = 0L;
    }

    /**
     * Commits a declaration batch and stages <b>the very plan object</b> that was
     * published — the same handoff the facade runtime performs at its commit point.
     */
    private long commitAndStage(DynamicDefinition... definitions) {
        DynamicCandidateRegistryPlan plan = store.beginBatch();
        for (DynamicDefinition definition : definitions) {
            plan.stageExistingDefinition(definition, "server_scripts/entry.js");
        }
        plan.preflight();
        plan.publish();
        coordinator.stage(plan, RecordingTxnSupport.targetState(store));
        return plan.generation();
    }

    private long commitAndActivate(DynamicDefinition... definitions) {
        long generation = commitAndStage(definitions);
        coordinator.pump();
        return generation;
    }

    // ---- AC1: order and observability ----

    @Test
    void phasesRunInTicketOrderAndEachPhaseResultIsObservable() {
        transport = new RecordingTxnSupport.ScriptedTransport("alice", "bob");
        coordinator = new DynamicRegistryTransactionCoordinator(adapter, transport, 10_000L, () -> clock);

        long generation = commitAndStage(RecordingTxnSupport.item("mymod:ruby", 16, "epic"));
        coordinator.pump();
        coordinator.onAck("alice", generation, true, null);
        coordinator.onAck("bob", generation, true, null);

        List<DynamicRegistryTransactionCoordinator.Phase> order = coordinator.phaseLog().stream()
                .map(DynamicRegistryTransactionCoordinator.PhaseRecord::phase).toList();
        assertEquals(List.of(
                DynamicRegistryTransactionCoordinator.Phase.PREFLIGHT,
                DynamicRegistryTransactionCoordinator.Phase.SERVER_PREPARE,
                DynamicRegistryTransactionCoordinator.Phase.CLIENT_PREPARE,
                DynamicRegistryTransactionCoordinator.Phase.ACK,
                DynamicRegistryTransactionCoordinator.Phase.COMMIT), order);
        // every record carries generation, owner and domain (AC1 observability)
        coordinator.phaseLog().forEach(record -> {
            assertTrue(record.passed(), "phase " + record.phase() + " should pass: " + record);
            assertEquals(generation, record.generation());
            assertNotNull(record.owner());
            assertNotNull(record.domain());
            assertNull(record.errorSource(), "passed phases carry no error source");
        });
    }

    @Test
    void singleNodeCommitsImmediatelyWhenNoParticipantsAreConnected() {
        commitAndActivate(RecordingTxnSupport.soundEvent("mymod:boom", 16.0f));
        assertEquals(DynamicRegistryTransactionCoordinator.Status.COMMITTED, coordinator.status());
        assertEquals(1, adapter.activateCalls.size());
        assertEquals(1, transport.ofKind(DynamicSyncMessage.Kind.COMMIT).size(),
                "commit is broadcast even with zero remote participants");
    }

    // ---- AC2: whole-batch abort keeps old active serving ----

    @Test
    void conflictedBatchAbortsAndOldActiveKeepsServing() {
        DynamicDefinition rubyV1 = RecordingTxnSupport.item("mymod:ruby", 16, "epic");
        long firstGeneration = commitAndActivate(rubyV1);
        assertEquals(1, adapter.activateCalls.size());

        // Same key, changed definition: the first-version rule rejects the batch at the
        // declaration ledger's joint preflight — the conflicted batch is never staged
        // for activation at all (the only staging path is a committed declaration).
        DynamicDefinition rubyV2 = RecordingTxnSupport.item("mymod:ruby", 32, "epic");
        DynamicCandidateRegistryPlan conflicted = store.beginBatch();
        conflicted.stageExistingDefinition(rubyV2, "server_scripts/entry.js");
        com.tkisor.nekojs.core.state.GlobalStateException conflict =
                assertThrows(com.tkisor.nekojs.core.state.GlobalStateException.class, conflicted::preflight);
        assertEquals("dynamic-registry-conflict", conflict.domain());
        coordinator.stage(conflicted, RecordingTxnSupport.targetState(store));
        // The staged conflicted batch aborts at the transaction PREFLIGHT re-check: the
        // ledger it is re-checked against still exposes the old definition.
        coordinator.pump();

        assertEquals(DynamicRegistryTransactionCoordinator.Status.ABORTED, coordinator.status());
        var failed = coordinator.phaseLog().stream()
                .filter(r -> !r.passed()).findFirst().orElseThrow();
        assertEquals(DynamicRegistryTransactionCoordinator.Phase.PREFLIGHT, failed.phase());
        assertEquals("dynamic-registry-conflict", failed.domain());
        assertEquals("preflight:dynamic-registry-conflict", failed.errorSource());

        // Old active keeps serving: adapter live state and watermark unchanged,
        // server prepare never ran for the conflicted batch
        assertEquals(firstGeneration, coordinator.activatedGeneration());
        assertEquals(1, adapter.activateCalls.size(), "conflicted batch must not reach activation");
        assertEquals(1, adapter.prepareCalls.size(), "conflicted batch must not even reach server prepare");
        assertEquals(rubyV1.fingerprint(), adapter.live.get("minecraft:item|mymod:ruby"));
        assertEquals(rubyV1.fingerprint(), store.exposedEntry("minecraft:item|mymod:ruby").definition().fingerprint(),
                "the declaration ledger keeps the old (still-serving) definition");
        assertEquals(0, coordinator.queuedBatchCount(), "the aborted batch is discarded, not queued");
        assertEquals(1, transport.ofKind(DynamicSyncMessage.Kind.ABORT).size(),
                "participants are told to discard their staged prepare");
    }

    @Test
    void clientRejectAbortsTheBatchWithNoActivationAnywhere() {
        transport = new RecordingTxnSupport.ScriptedTransport("alice", "bob");
        coordinator = new DynamicRegistryTransactionCoordinator(adapter, transport, 10_000L, () -> clock);
        long generation = commitAndStage(RecordingTxnSupport.item("mymod:ruby", 16, "epic"));
        coordinator.pump();
        assertEquals(DynamicRegistryTransactionCoordinator.Status.AWAITING_ACK, coordinator.status());

        coordinator.onAck("alice", generation, true, null);
        assertEquals(DynamicRegistryTransactionCoordinator.Status.AWAITING_ACK, coordinator.status());
        coordinator.onAck("bob", generation, false, "conflict:minecraft:item|mymod:ruby");

        assertEquals(DynamicRegistryTransactionCoordinator.Status.ABORTED, coordinator.status());
        var failed = coordinator.phaseLog().stream().filter(r -> !r.passed()).findFirst().orElseThrow();
        assertEquals(DynamicRegistryTransactionCoordinator.Phase.ACK, failed.phase());
        assertEquals("bob", failed.owner(), "the error source participant is observable");
        assertEquals("participant:bob:rejected", failed.errorSource());
        assertEquals(0, adapter.activateCalls.size(), "no partial registration anywhere");
        assertEquals(1, transport.ofKind(DynamicSyncMessage.Kind.ABORT).size());
    }

    @Test
    void ackTimeoutAbortsTheBatchWithADefinedOutcome() {
        transport = new RecordingTxnSupport.ScriptedTransport("alice");
        coordinator = new DynamicRegistryTransactionCoordinator(adapter, transport, 10_000L, () -> clock);
        long generation = commitAndStage(RecordingTxnSupport.mobEffect("mymod:wither_touch", "harmful", 0x8B0000));
        coordinator.pump();
        assertEquals(DynamicRegistryTransactionCoordinator.Status.AWAITING_ACK, coordinator.status());

        clock = 10_000L;
        coordinator.tick(clock);

        assertEquals(DynamicRegistryTransactionCoordinator.Status.ABORTED, coordinator.status());
        var failed = coordinator.phaseLog().stream().filter(r -> !r.passed()).findFirst().orElseThrow();
        assertEquals("ack-timeout", failed.errorSource());
        assertEquals(generation, failed.generation());
        assertEquals(0, adapter.activateCalls.size());
    }

    @Test
    void participantDisconnectBeforeAckAbortsTheBatch() {
        transport = new RecordingTxnSupport.ScriptedTransport("alice");
        coordinator = new DynamicRegistryTransactionCoordinator(adapter, transport, 10_000L, () -> clock);
        commitAndStage(RecordingTxnSupport.item("mymod:ruby", 16, "epic"));
        coordinator.pump();

        coordinator.onParticipantLeft("alice");

        assertEquals(DynamicRegistryTransactionCoordinator.Status.ABORTED, coordinator.status());
        var failed = coordinator.phaseLog().stream().filter(r -> !r.passed()).findFirst().orElseThrow();
        assertEquals("participant:alice:disconnected", failed.errorSource());
        assertEquals(0, adapter.activateCalls.size());
    }

    @Test
    void adapterPrepareRejectionAbortsBeforeAnyClientTraffic() {
        adapter.prepareRejection = new IllegalStateException(
                "dynamic registry activation is disabled: set [dynamicRegistry] enabled = true in engine.toml");
        commitAndStage(RecordingTxnSupport.item("mymod:ruby", 16, "epic"));
        coordinator.pump();

        assertEquals(DynamicRegistryTransactionCoordinator.Status.ABORTED, coordinator.status());
        var failed = coordinator.phaseLog().stream().filter(r -> !r.passed()).findFirst().orElseThrow();
        assertEquals(DynamicRegistryTransactionCoordinator.Phase.SERVER_PREPARE, failed.phase());
        assertEquals("adapter:prepare", failed.errorSource());
        assertTrue(failed.detail().contains("[dynamicRegistry] enabled"),
                "the rejection reason names the configuration gate");
        assertTrue(transport.ofKind(DynamicSyncMessage.Kind.PREPARE).isEmpty(),
                "no prepare leaves the server when the adapter rejects");
        assertEquals(0, adapter.activateCalls.size());
    }

    @Test
    void degradedActivationIsRolledBackAndThePreviousStateKeepsServing() {
        // batch 1 activates cleanly and becomes the old active state
        long firstGeneration = commitAndActivate(RecordingTxnSupport.item("mymod:old", 16, "common"));
        assertTrue(adapter.live.containsKey("minecraft:item|mymod:old"));
        assertEquals(1, transport.ofKind(DynamicSyncMessage.Kind.COMMIT).size());

        // batch 2: the adapter violates its no-throw contract mid-surgery (partial mutation)
        adapter.activateFailure = new IllegalStateException("registry exploded mid-surgery");
        adapter.mutatePartiallyBeforeFailure = true;
        commitAndStage(RecordingTxnSupport.item("mymod:new", 16, "epic"));
        coordinator.pump();

        assertEquals(DynamicRegistryTransactionCoordinator.Status.ABORTED, coordinator.status());
        assertEquals(1, adapter.rollbackCalls.size(), "a degraded activation demands exactly one rollback");
        assertEquals(adapter.activateCalls.get(1), adapter.rollbackCalls.get(0),
                "rollback receives the failed batch's requests");
        assertFalse(adapter.live.containsKey("minecraft:item|mymod:new"),
                "the partial mutation is undone — no half-success ID survives");
        assertTrue(adapter.live.containsKey("minecraft:item|mymod:old"),
                "the previously activated state keeps serving");
        assertEquals(firstGeneration, coordinator.activatedGeneration(),
                "the activated generation does not advance on a degraded activation");
        var failed = coordinator.phaseLog().stream().filter(r -> !r.passed()).findFirst().orElseThrow();
        assertEquals("adapter:activate", failed.errorSource());
        assertTrue(failed.detail().contains("restored the previously activated state"), failed.detail());
        assertEquals(1, transport.ofKind(DynamicSyncMessage.Kind.COMMIT).size(),
                "the degraded batch never broadcasts a commit");
    }

    @Test
    void failedClientActivationReportGetsACatchUpStateSyncRepair() {
        transport = new RecordingTxnSupport.ScriptedTransport("alice");
        coordinator = new DynamicRegistryTransactionCoordinator(adapter, transport, 10_000L, () -> clock);
        long generation = commitAndStage(RecordingTxnSupport.item("mymod:ruby", 16, "epic"));
        coordinator.pump();
        coordinator.onAck("alice", generation, true, null);
        assertEquals(DynamicRegistryTransactionCoordinator.Status.COMMITTED, coordinator.status());

        // the client fails its own activation after the server already committed
        coordinator.onParticipantActivationReport("alice", generation, false, "activation-failed:same-key conflict");

        var outcome = coordinator.syncOutcomes().stream()
                .filter(o -> "activation-failed-post-commit".equals(o.outcome())).findFirst().orElseThrow();
        assertEquals("alice", outcome.participantId());
        assertTrue(outcome.reason().contains("same-key conflict"));
        // repair: a fresh catch-up STATE_SYNC of the activated generation goes to that node
        var repairs = transport.ofKind(DynamicSyncMessage.Kind.STATE_SYNC).stream()
                .filter(sent -> "alice".equals(sent.participantId())).toList();
        assertEquals(1, repairs.size());
        assertEquals(generation, repairs.get(0).message().generation());
        // and the node completing that repair is a defined, observable outcome
        coordinator.onAck("alice", generation, true, null);
        assertTrue(coordinator.syncOutcomes().stream()
                .anyMatch(o -> "state-sync-completed".equals(o.outcome())));
    }

    @Test
    void closePreemptionAbortsInFlightBatchAndDiscardsQueuedBatches() {
        transport = new RecordingTxnSupport.ScriptedTransport("alice");
        coordinator = new DynamicRegistryTransactionCoordinator(adapter, transport, 10_000L, () -> clock);
        commitAndStage(RecordingTxnSupport.item("mymod:ruby", 16, "epic"));
        coordinator.pump();
        assertEquals(DynamicRegistryTransactionCoordinator.Status.AWAITING_ACK, coordinator.status());

        // a second batch commits its declarations while the first waits for an ack
        commitAndStage(RecordingTxnSupport.soundEvent("mymod:boom", 16.0f));
        assertEquals(1, coordinator.queuedBatchCount());

        assertTrue(coordinator.abortInFlight("server-stopping"));
        assertEquals(DynamicRegistryTransactionCoordinator.Status.ABORTED, coordinator.status());
        assertEquals(0, coordinator.queuedBatchCount(), "queued batches are discarded across a close boundary");
        assertEquals(0, adapter.activateCalls.size());
        assertTrue(coordinator.phaseLog().stream()
                .anyMatch(r -> "close-preemption:server-stopping".equals(r.errorSource())));
    }

    // ---- AC6/commit-once: exactly-once execution and defined late/duplicate outcomes ----

    @Test
    void lateAckAfterCommitHasDefinedOutcomeAndNoDoubleExecution() {
        transport = new RecordingTxnSupport.ScriptedTransport("alice", "bob");
        coordinator = new DynamicRegistryTransactionCoordinator(adapter, transport, 10_000L, () -> clock);
        long generation = commitAndStage(RecordingTxnSupport.item("mymod:ruby", 16, "epic"));
        coordinator.pump();
        coordinator.onAck("alice", generation, true, null);
        coordinator.onAck("bob", generation, true, null);
        assertEquals(DynamicRegistryTransactionCoordinator.Status.COMMITTED, coordinator.status());
        assertEquals(1, adapter.activateCalls.size());

        // a late (already-decided) ack: recorded outcome, no re-execution
        coordinator.onAck("alice", generation, true, null);
        assertTrue(coordinator.syncOutcomes().stream()
                .anyMatch(o -> "alice".equals(o.participantId())
                        && "late-ack-outside-transaction".equals(o.outcome())));
        assertEquals(1, adapter.activateCalls.size(), "commit executes exactly once");
        assertEquals(generation, coordinator.activatedGeneration());

        // an ack for an entirely unknown generation: same defined treatment
        coordinator.onAck("carol", generation + 100, true, null);
        assertTrue(coordinator.syncOutcomes().stream()
                .anyMatch(o -> "carol".equals(o.participantId())
                        && "late-ack-outside-transaction".equals(o.outcome())));
        assertEquals(1, adapter.activateCalls.size());
    }

    @Test
    void duplicateAckDuringAckPhaseIsIgnoredWithADefinedOutcome() {
        transport = new RecordingTxnSupport.ScriptedTransport("alice", "bob");
        coordinator = new DynamicRegistryTransactionCoordinator(adapter, transport, 10_000L, () -> clock);
        long generation = commitAndStage(RecordingTxnSupport.item("mymod:ruby", 16, "epic"));
        coordinator.pump();

        coordinator.onAck("alice", generation, true, null);
        coordinator.onAck("alice", generation, true, null); // duplicate
        assertTrue(coordinator.syncOutcomes().stream()
                .anyMatch(o -> "alice".equals(o.participantId()) && "duplicate-ack".equals(o.outcome())));
        assertEquals(DynamicRegistryTransactionCoordinator.Status.AWAITING_ACK, coordinator.status());

        coordinator.onAck("bob", generation, true, null);
        assertEquals(DynamicRegistryTransactionCoordinator.Status.COMMITTED, coordinator.status());
        assertEquals(1, adapter.activateCalls.size());
    }

    @Test
    void queuedBatchesRunSeriallyAfterTheInFlightOne() {
        long first = commitAndStage(RecordingTxnSupport.item("mymod:ruby", 16, "epic"));
        long second = commitAndStage(RecordingTxnSupport.soundEvent("mymod:boom", 16.0f));
        coordinator.pump();

        assertEquals(DynamicRegistryTransactionCoordinator.Status.COMMITTED, coordinator.status());
        assertEquals(2, adapter.activateCalls.size());
        assertEquals(second, coordinator.activatedGeneration());
        assertTrue(first < second, "generations advance monotonically");
        assertEquals(2, adapter.live.size(), "both batches' entries are live after both commit");
    }

    // ---- AC4: join / state-sync outcomes ----

    @Test
    void participantJoiningMidTransactionMustAckBeforeCommit() {
        transport = new RecordingTxnSupport.ScriptedTransport("alice");
        coordinator = new DynamicRegistryTransactionCoordinator(adapter, transport, 10_000L, () -> clock);
        long generation = commitAndStage(RecordingTxnSupport.item("mymod:ruby", 16, "epic"));
        coordinator.pump();

        coordinator.onParticipantJoined("bob");
        assertEquals(1, transport.ofKind(DynamicSyncMessage.Kind.PREPARE).stream()
                .filter(s -> "bob".equals(s.participantId())).count(),
                "the newcomer receives the in-flight prepare");
        coordinator.onAck("alice", generation, true, null);
        assertEquals(DynamicRegistryTransactionCoordinator.Status.AWAITING_ACK, coordinator.status(),
                "commit waits for the newcomer: no node left behind by a committing majority");

        coordinator.onAck("bob", generation, true, null);
        assertEquals(DynamicRegistryTransactionCoordinator.Status.COMMITTED, coordinator.status());
        assertEquals(1, adapter.activateCalls.size());
    }

    @Test
    void lateJoinerReceivesStateSyncOfTheActivatedState() {
        long generation = commitAndActivate(RecordingTxnSupport.item("mymod:ruby", 16, "epic"));

        coordinator.onParticipantJoined("carol");
        var sent = transport.ofKind(DynamicSyncMessage.Kind.STATE_SYNC);
        assertEquals(1, sent.size());
        assertEquals("carol", sent.get(0).participantId());
        assertEquals(generation, sent.get(0).message().generation(),
                "state-sync never activates a newer generation than the server's watermark");
        assertEquals(1, sent.get(0).message().entries().size());

        coordinator.onAck("carol", generation, true, null);
        assertTrue(coordinator.syncOutcomes().stream().anyMatch(
                o -> "carol".equals(o.participantId()) && "state-sync-completed".equals(o.outcome())));
    }
}
