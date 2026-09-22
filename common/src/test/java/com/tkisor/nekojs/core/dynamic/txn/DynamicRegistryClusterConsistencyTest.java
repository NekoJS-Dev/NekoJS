package com.tkisor.nekojs.core.dynamic.txn;

import com.tkisor.nekojs.core.dynamic.plan.DynamicDefinition;
import com.tkisor.nekojs.core.dynamic.plan.DynamicRegistryPlanStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Ticket 21 cross-node runtime smoke (JVM-level cluster): a server coordinator
 * plus real {@link DynamicRegistryClientParticipant} nodes wired through a
 * recording transport, with manually driven deterministic delivery. Success
 * assertions observe the <b>final visible state on every activated node</b>
 * (server adapter live map + each client's ledger/watermark) — never the mere
 * existence of prepare/ack messages (AC3); a rejected batch leaves <b>no</b>
 * partial registration on any node (AC2).
 *
 * <p>The adapters here are JVM doubles: these fixtures verify the transaction and
 * sync gates, not any platform Adapter — per-type activation conclusions for the
 * real target Adapter are recorded separately (capability table in the ticket 21
 * baseline REPORT).
 */
class DynamicRegistryClusterConsistencyTest {

    /** Records every outbound message; delivery is driven explicitly by the test. */
    private static final class ClusterTransport implements DynamicSyncTransport {
        record Outbound(String participantId, DynamicSyncMessage message) {}

        final List<String> participants;
        final List<Outbound> outbound = new ArrayList<>();

        ClusterTransport(String... participants) {
            this.participants = List.of(participants);
        }

        @Override
        public List<String> participants() {
            return participants;
        }

        @Override
        public void send(String participantId, DynamicSyncMessage message) {
            outbound.add(new Outbound(participantId, message));
        }

        @Override
        public void broadcast(DynamicSyncMessage message) {
            outbound.add(new Outbound(null, message));
        }
    }

    private DynamicRegistryPlanStore serverStore;
    private RecordingTxnSupport.RecordingAdapter serverAdapter;
    private ClusterTransport transport;
    private DynamicRegistryTransactionCoordinator server;
    private final Map<String, DynamicRegistryClientParticipant> clients = new LinkedHashMap<>();
    private final Map<DynamicRegistryClientParticipant, RecordingTxnSupport.RecordingAdapter> clientAdapters =
            new java.util.IdentityHashMap<>();
    /** Participant whose delivery is currently being answered (ack routing). */
    private String deliveringTo;
    private long clock;

    @BeforeEach
    void setUp() {
        serverStore = new DynamicRegistryPlanStore();
        serverAdapter = new RecordingTxnSupport.RecordingAdapter();
    }

    private void cluster(String... participantIds) {
        transport = new ClusterTransport(participantIds);
        server = new DynamicRegistryTransactionCoordinator(serverAdapter, transport, 10_000L, () -> clock);
        for (String id : participantIds) {
            RecordingTxnSupport.RecordingAdapter adapter = new RecordingTxnSupport.RecordingAdapter();
            DynamicRegistryClientParticipant client = new DynamicRegistryClientParticipant(adapter);
            clients.put(id, client);
            clientAdapters.put(client, adapter);
        }
    }

    private RecordingTxnSupport.RecordingAdapter adapterOf(DynamicRegistryClientParticipant client) {
        return clientAdapters.get(client);
    }

    private long commitBatchOnServer(DynamicDefinition... definitions) {
        var plan = serverStore.beginBatch();
        for (DynamicDefinition definition : definitions) {
            plan.stageExistingDefinition(definition, "server_scripts/entry.js");
        }
        plan.preflight();
        plan.publish();
        server.stage(plan, RecordingTxnSupport.targetState(serverStore));
        return plan.generation();
    }

    /** Delivers every queued outbound message in order (broadcasts reach all clients). */
    private void deliverAll() {
        deliverAllDropping(null);
    }

    /**
     * Delivers queued messages, dropping the ones addressed to a vanished participant
     * (the transport contract: a delivery to a vanished participant is dropped).
     */
    private void deliverAllDropping(String vanishedParticipant) {
        while (!transport.outbound.isEmpty()) {
            ClusterTransport.Outbound next = transport.outbound.remove(0);
            if (next.participantId() != null && next.participantId().equals(vanishedParticipant)) {
                continue;
            }
            if (next.participantId() == null) {
                for (DynamicRegistryClientParticipant client : clients.values()) {
                    deliverTo(client, next.message());
                }
            } else {
                DynamicRegistryClientParticipant client = clients.get(next.participantId());
                assertNotNull(client, "message for unknown participant " + next.participantId());
                deliveringTo = next.participantId();
                deliverTo(client, next.message());
            }
        }
    }

    private void deliverTo(DynamicRegistryClientParticipant client, DynamicSyncMessage message) {
        switch (message.kind()) {
            case PREPARE -> {
                DynamicRegistryClientParticipant.PrepareDecision decision = client.onPrepare(message);
                server.onAck(deliveringTo, message.generation(), decision.accepted(), decision.reason());
            }
            case STATE_SYNC -> {
                DynamicRegistryClientParticipant.PrepareDecision decision = client.onStateSync(message);
                server.onAck(deliveringTo, message.generation(), decision.accepted(), decision.reason());
            }
            case COMMIT -> client.onCommit(message);
            case ABORT -> client.onAbort(message);
        }
    }

    // ---- AC3: final consistent visibility on all activated nodes ----

    @Test
    void allNodesReachTheSameActivatedStateAfterAckAndCommit() {
        cluster("alice", "bob");
        long generation = commitBatchOnServer(
                RecordingTxnSupport.item("mymod:ruby", 16, "epic"),
                RecordingTxnSupport.soundEvent("mymod:boom", 16.0f),
                RecordingTxnSupport.mobEffect("mymod:wither_touch", "harmful", 0x8B0000));
        server.pump();
        deliverAll();

        assertEquals(DynamicRegistryTransactionCoordinator.Status.COMMITTED, server.status());
        assertEquals(generation, server.activatedGeneration());
        assertEquals(1, serverAdapter.activateCalls.size(), "server executed the batch exactly once");

        // final visibility on EVERY activated node: same watermark, same live state,
        // same visible fingerprints as the server's activated state
        Map<String, String> serverLive = serverAdapter.live;
        assertEquals(3, serverLive.size());
        for (DynamicRegistryClientParticipant client : clients.values()) {
            assertEquals(generation, client.activatedGeneration(), "client watermark matches the server");
            assertEquals(serverLive, adapterOf(client).live, "client live state is identical to the server's");
            for (Map.Entry<String, DynamicRegistryPlanStore.ExposedEntry> exposed
                    : client.ledger().exposedSnapshot().entrySet()) {
                assertEquals(serverLive.get(exposed.getKey()), exposed.getValue().definition().fingerprint(),
                        "visible fingerprint of " + exposed.getKey() + " matches on every node");
            }
        }
    }

    // ---- AC2: one rejection leaves no partial registration anywhere ----

    @Test
    void oneClientRejectsAndNoNodeActivatesAnything() {
        cluster("alice", "bob");
        // bob carries a diverged local declaration of the same key (e.g. from a
        // locally-run script generation): the server's prepare conflicts with it
        var bobSeed = clients.get("bob").ledger().beginBatch();
        bobSeed.stageExistingDefinition(RecordingTxnSupport.item("mymod:ruby", 64, "common"), "local_scripts/b.js");
        bobSeed.preflight();
        bobSeed.publish();

        long generation = commitBatchOnServer(RecordingTxnSupport.item("mymod:ruby", 16, "epic"));
        server.pump();
        deliverAll();

        assertEquals(DynamicRegistryTransactionCoordinator.Status.ABORTED, server.status(),
                "bob's rejection aborts the whole batch");
        assertEquals(0, serverAdapter.activateCalls.size(), "no partial registration on the server");
        for (DynamicRegistryClientParticipant client : clients.values()) {
            assertEquals(-1L, client.activatedGeneration(), "no node activated the aborted batch");
            assertEquals(-1L, client.stagedGeneration(), "every staged prepare is discarded and cleaned");
            assertEquals(0, adapterOf(client).activateCalls.size(), "no half-success ids anywhere");
        }
        // alice's ledger has no writes; bob keeps only his own pre-existing seed
        assertTrue(clients.get("alice").ledger().exposedSnapshot().isEmpty());
        assertEquals(1, clients.get("bob").ledger().exposedSnapshot().size());
        assertEquals(generation, server.lastSummary().generation());
        assertFalse(server.lastSummary().committed());
    }

    // ---- AC4: late joiner catch-up; disconnect has a defined cross-node outcome ----

    @Test
    void lateJoinerCatchesUpViaStateSyncToTheSameState() {
        cluster("alice");
        long generation = commitBatchOnServer(RecordingTxnSupport.item("mymod:ruby", 16, "epic"));
        server.pump();
        deliverAll();
        assertEquals(generation, server.activatedGeneration());

        // carol joins after activation: the server sends a catch-up STATE_SYNC
        RecordingTxnSupport.RecordingAdapter carolAdapter = new RecordingTxnSupport.RecordingAdapter();
        DynamicRegistryClientParticipant carol = new DynamicRegistryClientParticipant(carolAdapter);
        clients.put("carol", carol);
        clientAdapters.put(carol, carolAdapter);
        server.onParticipantJoined("carol");
        deliverAll();

        assertEquals(generation, carol.activatedGeneration(),
                "catch-up reaches exactly the server's activated generation, never newer");
        assertEquals(serverAdapter.live, carolAdapter.live, "carol's final visible state matches the server's");
        assertEquals(generation, server.activatedGeneration(), "the server activated nothing new");
    }

    @Test
    void clientDisconnectBeforeAckAbortsEverywhere() {
        cluster("alice", "bob");
        commitBatchOnServer(RecordingTxnSupport.item("mymod:ruby", 16, "epic"));
        server.pump();

        // alice disconnects before her prepare is delivered/acked: the in-flight
        // batch aborts (a node that may hold a diverged prepare must never see a
        // mixed generation); deliveries to the vanished participant are dropped.
        server.onParticipantLeft("alice");
        assertEquals(DynamicRegistryTransactionCoordinator.Status.ABORTED, server.status());
        assertEquals(0, serverAdapter.activateCalls.size());

        deliverAllDropping("alice"); // ABORT broadcast: bob discards his staged prepare
        for (DynamicRegistryClientParticipant client : clients.values()) {
            assertEquals(-1L, client.activatedGeneration());
            assertEquals(-1L, client.stagedGeneration(), "bob's staged prepare is discarded on abort");
        }
    }
}
