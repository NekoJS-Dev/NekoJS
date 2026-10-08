//? if neoforge {
//? if >=26 {
package com.tkisor.nekojs.dynamic;

import com.tkisor.nekojs.core.dynamic.plan.DynamicAdapterRequest;
import com.tkisor.nekojs.core.dynamic.plan.DynamicDefinitionType;
import com.tkisor.nekojs.core.dynamic.plan.DynamicRegistryPlanStore;
import com.tkisor.nekojs.core.dynamic.plan.DynamicSoundEventBuilder;
import com.tkisor.nekojs.core.dynamic.txn.DynamicRegistryAdapter;
import com.tkisor.nekojs.core.dynamic.txn.DynamicRegistryTransactionCoordinator;
import com.tkisor.nekojs.core.dynamic.txn.DynamicSyncMessage;
import com.tkisor.nekojs.core.dynamic.txn.DynamicSyncReply;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DynamicConfigurationParticipantsTest {
    private final List<String> playing = new ArrayList<>();
    private final List<DynamicSyncMessage> playMessages = new ArrayList<>();
    private final NeoForgeDynamicSyncTransport transport = new NeoForgeDynamicSyncTransport(
            () -> List.copyOf(playing), (id, message) -> playMessages.add(message));
    private final List<List<DynamicAdapterRequest>> activations = new ArrayList<>();
    private final DynamicRegistryTransactionCoordinator engine = new DynamicRegistryTransactionCoordinator(
            new DynamicRegistryAdapter() {
                public void prepareActivation(List<DynamicAdapterRequest> requests) {}
                public void activate(List<DynamicAdapterRequest> requests) { activations.add(List.copyOf(requests)); }
                public void rollbackActivation(List<DynamicAdapterRequest> requests) {}
            }, transport, 10_000, () -> 0);
    private final DynamicRegistryPlanStore store = new DynamicRegistryPlanStore();

    private static final class Connection implements NeoForgeDynamicSyncTransport.ConfigurationConnection {
        final List<DynamicSyncMessage> messages = new ArrayList<>();
        boolean connected = true;
        int finishes;
        public String participantId() { return "configuration-client"; }
        public boolean connected() { return connected; }
        public void send(DynamicSyncMessage message) { messages.add(message); }
        public void finishTask() { finishes++; }
        public void disconnect() { connected = false; }
    }

    private long stage(String id) {
        var plan = store.beginBatch();
        plan.add(DynamicDefinitionType.SOUND_EVENT, "proof:" + id, new DynamicSoundEventBuilder(), "test", "test");
        plan.preflight();
        plan.publish();
        var target = store.exposedSnapshot().values().stream()
                .map(entry -> new DynamicSyncMessage.Entry(entry.definition(), entry.ownerScriptId())).toList();
        engine.stage(plan, target);
        engine.pump();
        return plan.generation();
    }

    private void reply(Connection connection, DynamicSyncReply reply) {
        if (reply.kind() == DynamicSyncReply.Kind.ACK) {
            engine.onAck(connection.participantId(), reply.generation(), Boolean.TRUE.equals(reply.accepted()), reply.reason());
        } else {
            engine.onParticipantActivationReport(connection.participantId(), reply.generation(),
                    Boolean.TRUE.equals(reply.activated()), reply.detail());
        }
        transport.onReply(connection.participantId(), reply);
    }

    @Test
    void emptyStateFinishesOnceAndRemainsEnrolledUntilPlayHandoff() {
        var connection = new Connection();
        transport.joinConfiguration(connection, engine);
        assertEquals(1, connection.finishes);
        assertEquals(List.of(connection.participantId()), transport.participants());
        playing.add(connection.participantId());
        assertTrue(transport.handoff(connection.participantId()));
        assertFalse(transport.handoff(connection.participantId()));
        assertEquals(List.of(connection.participantId()), transport.participants());
        assertTrue(engine.syncOutcomes().isEmpty(), "handoff must not synthesize a leave");
        stage("after_handoff");
        assertEquals(DynamicSyncMessage.Kind.PREPARE, playMessages.getLast().kind());
    }

    @Test
    void activatedStateRequiresRealCatchUpAckBeforeFrozenRegistryTask() {
        long generation = stage("existing");
        var connection = new Connection();
        transport.joinConfiguration(connection, engine);
        assertEquals(DynamicSyncMessage.Kind.STATE_SYNC, connection.messages.getLast().kind());
        assertEquals(0, connection.finishes);
        reply(connection, DynamicSyncReply.ack(generation + 1, true, null));
        assertEquals(0, connection.finishes, "wrong-generation ACK cannot release the task");
        reply(connection, DynamicSyncReply.ack(generation, true, null));
        reply(connection, DynamicSyncReply.ack(generation, true, null));
        assertEquals(1, connection.finishes);
    }

    @Test
    void joinDuringTransactionWaitsForCommitActivationRatherThanPrepareAck() {
        playing.add("existing-player");
        long generation = stage("in_flight");
        var connection = new Connection();
        transport.joinConfiguration(connection, engine);
        assertEquals(DynamicSyncMessage.Kind.PREPARE, connection.messages.getLast().kind());
        engine.onAck("existing-player", generation, true, null);
        reply(connection, DynamicSyncReply.ack(generation, true, null));
        assertEquals(DynamicRegistryTransactionCoordinator.Status.COMMITTED, engine.status());
        assertEquals(DynamicSyncMessage.Kind.COMMIT, connection.messages.getLast().kind());
        assertEquals(0, connection.finishes);
        reply(connection, DynamicSyncReply.activationReport(generation, true, null));
        assertEquals(1, connection.finishes);
    }

    @Test
    void livePrepareSupersedesCatchUpAckAndQueuedCommitSupersedesOlderActivationReport() {
        long first = stage("existing");
        var connection = new Connection();
        transport.joinConfiguration(connection, engine);
        long second = stage("second");
        reply(connection, DynamicSyncReply.ack(first, true, null));
        assertEquals(0, connection.finishes);
        long third = stage("third");
        reply(connection, DynamicSyncReply.ack(second, true, null));
        assertEquals(DynamicSyncMessage.Kind.PREPARE, connection.messages.getLast().kind());
        reply(connection, DynamicSyncReply.activationReport(second, true, null));
        assertEquals(0, connection.finishes);
        reply(connection, DynamicSyncReply.ack(third, true, null));
        assertEquals(0, connection.finishes);
        reply(connection, DynamicSyncReply.activationReport(third, true, null));
        assertEquals(1, connection.finishes);
        assertEquals(3, activations.size());
    }

    @Test
    void clientRejectionAbortsWholeBatchAndDisconnectsConfiguration() {
        var connection = new Connection();
        playing.add("existing-player");
        long generation = stage("rejected");
        transport.joinConfiguration(connection, engine);
        reply(connection, DynamicSyncReply.ack(generation, false, "gate-disabled"));
        assertEquals(DynamicRegistryTransactionCoordinator.Status.ABORTED, engine.status());
        assertTrue(activations.isEmpty());
        assertFalse(connection.connected);
        assertEquals(0, connection.finishes);
        transport.tick(engine, 0);
        assertEquals(List.of("existing-player"), transport.participants());
    }

    @Test
    void disconnectBeforeAckAbortsWithoutReentrantBroadcast() {
        var connection = new Connection();
        playing.add("existing-player");
        stage("disconnected");
        transport.joinConfiguration(connection, engine);
        connection.connected = false;
        transport.tick(engine, 0);
        assertEquals(DynamicRegistryTransactionCoordinator.Status.ABORTED, engine.status());
        assertTrue(activations.isEmpty());
        assertEquals(0, connection.finishes);
        assertEquals(DynamicSyncMessage.Kind.ABORT, playMessages.getLast().kind());
    }

    @Test
    void catchUpTimeoutDisconnectsAndReleasesPendingCoordinatorState() {
        stage("existing");
        var connection = new Connection();
        transport.joinConfiguration(connection, engine);
        transport.tick(engine, Long.MAX_VALUE);
        assertFalse(connection.connected);
        assertTrue(transport.participants().isEmpty());
        assertEquals(0, connection.finishes);
        assertEquals("left", engine.syncOutcomes().getLast().outcome());
    }

    @Test
    void activationFailureCannotReleaseConfigurationEvenWhenCoordinatorOffersRetry() {
        playing.add("existing-player");
        long generation = stage("activation_failure");
        var connection = new Connection();
        transport.joinConfiguration(connection, engine);
        engine.onAck("existing-player", generation, true, null);
        reply(connection, DynamicSyncReply.ack(generation, true, null));
        reply(connection, DynamicSyncReply.activationReport(generation, false, "adapter-failure"));
        assertEquals(0, connection.finishes);
        assertFalse(connection.connected);
    }

    @Test
    void closingEngineDisconnectsAndReleasesAllConfigurationConnections() {
        stage("existing");
        var connection = new Connection();
        transport.joinConfiguration(connection, engine);
        engine.close("server-stopped");
        assertFalse(connection.connected);
        assertTrue(transport.participants().isEmpty());
        assertEquals(0, connection.finishes);
    }
}
//?}
//?}
