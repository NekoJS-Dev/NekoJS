package com.tkisor.nekojs.core.dynamic.txn;

import com.tkisor.nekojs.core.dynamic.plan.DynamicRegistryPlanStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Ticket 21, AC3/AC4/AC6/AC7 (client side): one node's view of the batch
 * transaction. Sync-incomplete commits never activate a new generation; duplicate
 * and late messages have defined outcomes (no silent no-op); disconnect keeps the
 * ledger and watermark for rejoin; the local first-version conflict rule rejects
 * diverged server state without silent overwrite; success assertions observe the
 * node's final visible state, never the mere existence of prepare/ack messages.
 */
class DynamicRegistryClientParticipantTest {

    private RecordingTxnSupport.RecordingAdapter adapter;
    private DynamicRegistryClientParticipant client;

    @BeforeEach
    void setUp() {
        adapter = new RecordingTxnSupport.RecordingAdapter();
        client = new DynamicRegistryClientParticipant(adapter);
    }

    private DynamicSyncMessage singleItemPrepare(long generation, String id, int maxStackSize) {
        return DynamicSyncMessage.prepare(generation, List.of(new DynamicSyncMessage.Entry(
                RecordingTxnSupport.item(id, maxStackSize, "common"), "server_scripts/entry.js")));
    }

    // ---- AC6: exactly-once activation ----

    @Test
    void prepareThenCommitActivatesExactlyOnceAndDuplicateCommitIsANoOp() {
        DynamicSyncMessage prepare = singleItemPrepare(1, "mymod:ruby", 16);
        DynamicRegistryClientParticipant.PrepareDecision decision = client.onPrepare(prepare);
        assertTrue(decision.accepted(), "prepare accepted: " + decision.reason());
        assertEquals(1, client.stagedGeneration());

        DynamicRegistryClientParticipant.ActivationResult result = client.onCommit(DynamicSyncMessage.commit(1));
        assertTrue(result.activated(), "commit activates the staged generation: " + result);
        assertEquals(1, client.activatedGeneration());
        assertEquals(1, adapter.activateCalls.size(), "the new generation executes exactly once");

        // duplicate commit for the already-activated generation: defined no-op
        DynamicRegistryClientParticipant.ActivationResult duplicate = client.onCommit(DynamicSyncMessage.commit(1));
        assertFalse(duplicate.activated());
        assertEquals("already-activated", duplicate.outcome(),
                "a duplicate commit is a recorded no-op, not a silent one");
        assertEquals(1, client.activatedGeneration());
        assertEquals(1, adapter.activateCalls.size(), "commit-once: no double execution");
        assertTrue(client.eventLog().stream().anyMatch(e -> e.startsWith("already-activated")));
    }

    // ---- AC4: sync-incomplete nodes never activate ----

    @Test
    void commitWithoutMatchingPrepareNeverActivates() {
        DynamicRegistryClientParticipant.ActivationResult result =
                client.onCommit(DynamicSyncMessage.commit(7));
        assertFalse(result.activated());
        assertEquals("commit-without-matching-prepare", result.outcome());
        assertEquals(-1L, client.activatedGeneration(), "no new generation activated");
        assertEquals(0, adapter.activateCalls.size());
    }

    @Test
    void commitForADifferentGenerationThanTheStagedPrepareNeverActivates() {
        client.onPrepare(singleItemPrepare(2, "mymod:ruby", 16));
        DynamicRegistryClientParticipant.ActivationResult result =
                client.onCommit(DynamicSyncMessage.commit(3));
        assertFalse(result.activated());
        assertEquals("commit-without-matching-prepare", result.outcome());
        assertEquals(2, client.stagedGeneration(), "the staged prepare survives for its own generation");
        assertEquals(0, adapter.activateCalls.size());
    }

    @Test
    void prepareForAnAlreadyActivatedGenerationIsAcceptedAsDuplicateWithoutStaging() {
        client.onPrepare(singleItemPrepare(1, "mymod:ruby", 16));
        client.onCommit(DynamicSyncMessage.commit(1));
        assertEquals(1, adapter.activateCalls.size());

        DynamicRegistryClientParticipant.PrepareDecision duplicate =
                client.onPrepare(singleItemPrepare(1, "mymod:ruby", 16));
        assertTrue(duplicate.accepted(), "duplicate-generation prepare is accepted so the server can finish");
        assertEquals("duplicate-generation", duplicate.reason());
        assertEquals(-1L, client.stagedGeneration(), "nothing is re-staged for an activated generation");
    }

    // ---- AC7: local first-version conflict rule (no silent overwrite) ----

    @Test
    void conflictingTargetStateIsRejectedAsAWhole() {
        // the node already activated ruby with maxStackSize 16 (fingerprint A)
        client.onPrepare(singleItemPrepare(1, "mymod:ruby", 16));
        client.onCommit(DynamicSyncMessage.commit(1));

        // a server batch claims the same key with a different definition
        DynamicRegistryClientParticipant.PrepareDecision decision =
                client.onPrepare(singleItemPrepare(2, "mymod:ruby", 32));
        assertFalse(decision.accepted(), "changed definition on an exposed key rejects the whole message");
        assertTrue(decision.reason().startsWith("conflict:minecraft:item|mymod:ruby"),
                "the rejection names the conflicting key: " + decision.reason());
        assertEquals(-1L, client.stagedGeneration(), "nothing staged");
        assertEquals(1, client.activatedGeneration(), "the old activated state keeps serving");
        assertEquals(1, adapter.activateCalls.size());
    }

    // ---- AC4: abort / disconnect / rejoin / degraded adapter ----

    @Test
    void abortDiscardsTheStagedPrepare() {
        client.onPrepare(singleItemPrepare(2, "mymod:ruby", 16));
        assertEquals(2, client.stagedGeneration());

        client.onAbort(DynamicSyncMessage.abort(2, "participant-rejected:bob"));

        assertEquals(-1L, client.stagedGeneration(), "the staged prepare is discarded and cleaned");
        assertEquals(-1L, client.activatedGeneration());
        assertEquals(0, adapter.activateCalls.size());
        // a late commit for the aborted generation has a defined non-activating outcome
        assertEquals("commit-without-matching-prepare", client.onCommit(DynamicSyncMessage.commit(2)).outcome());
    }

    @Test
    void adapterActivationFailureIsAnExplicitDegradedResult() {
        adapter.activateFailure = new IllegalStateException("registry is frozen");
        client.onPrepare(singleItemPrepare(1, "mymod:ruby", 16));

        DynamicRegistryClientParticipant.ActivationResult result = client.onCommit(DynamicSyncMessage.commit(1));
        assertFalse(result.activated());
        assertTrue(result.outcome().startsWith("activation-failed:"),
                "the failure is an explicit outcome: " + result.outcome());
        assertEquals(-1L, client.activatedGeneration());
        assertEquals(-1L, client.stagedGeneration(), "the failed staged prepare is discarded");
    }

    @Test
    void disconnectDiscardsStagedButKeepsLedgerAndWatermarkForRejoin() {
        client.onPrepare(singleItemPrepare(1, "mymod:ruby", 16));
        client.onCommit(DynamicSyncMessage.commit(1));

        client.onPrepare(singleItemPrepare(2, "mymod:sapphire", 16));
        assertEquals(2, client.stagedGeneration());
        client.onDisconnect();
        assertEquals(-1L, client.stagedGeneration(), "disconnect discards the staged prepare");
        assertEquals(1, client.activatedGeneration(), "the watermark survives the disconnect");
        assertNotNull(client.ledger().exposedEntry("minecraft:item|mymod:ruby"),
                "the activated ledger survives (no mid-session unregister)");

        // rejoin: the server's catch-up state sync of the already-activated state
        DynamicSyncMessage catchUp = DynamicSyncMessage.stateSync(1, List.of(new DynamicSyncMessage.Entry(
                RecordingTxnSupport.item("mymod:ruby", 16, "common"), "server_scripts/entry.js")));
        DynamicRegistryClientParticipant.PrepareDecision decision = client.onStateSync(catchUp);
        assertTrue(decision.accepted());
        assertEquals(1, client.activatedGeneration(), "state sync of an old generation does not activate a newer one");
        assertEquals(1, adapter.activateCalls.size(), "idempotent: the activated generation is not re-executed");
    }

    // ---- AC3: final visible state, not message existence ----

    @Test
    void activatedStateIsFullyVisibleInTheNodesLedger() {
        List<DynamicSyncMessage.Entry> entries = List.of(
                new DynamicSyncMessage.Entry(RecordingTxnSupport.item("mymod:ruby", 16, "epic"), "server_scripts/a.js"),
                new DynamicSyncMessage.Entry(RecordingTxnSupport.soundEvent("mymod:boom", 16.0f), "server_scripts/b.js"),
                new DynamicSyncMessage.Entry(
                        RecordingTxnSupport.mobEffect("mymod:wither_touch", "harmful", 0x8B0000), "server_scripts/c.js"));
        client.onPrepare(DynamicSyncMessage.prepare(4, entries));
        DynamicRegistryClientParticipant.ActivationResult result = client.onCommit(DynamicSyncMessage.commit(4));

        assertTrue(result.activated());
        assertEquals(4, client.activatedGeneration());
        // final consistent visibility on this node: every entry is in the ledger with
        // the exact server fingerprint and owner claim
        for (DynamicSyncMessage.Entry entry : entries) {
            DynamicRegistryPlanStore.ExposedEntry exposed = client.ledger().exposedEntry(entry.key());
            assertNotNull(exposed, "missing visible entry " + entry.key());
            assertEquals(entry.definition().fingerprint(), exposed.definition().fingerprint());
            assertEquals(entry.ownerScriptId(), exposed.ownerScriptId());
        }
        assertEquals(1, adapter.activateCalls.size());
        assertEquals(3, adapter.live.size(), "all three frozen candidate types activated");
    }
}
