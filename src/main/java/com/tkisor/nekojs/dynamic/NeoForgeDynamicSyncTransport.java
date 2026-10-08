//? if neoforge {
//? if >=26 {
package com.tkisor.nekojs.dynamic;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.core.dynamic.txn.DynamicRegistryTransactionCoordinator;
import com.tkisor.nekojs.core.dynamic.txn.DynamicSyncMessage;
import com.tkisor.nekojs.core.dynamic.txn.DynamicSyncReply;
import com.tkisor.nekojs.core.dynamic.txn.DynamicSyncTransport;
import com.tkisor.nekojs.core.dynamic.txn.DynamicSyncWireCodec;
import com.tkisor.nekojs.network.DynamicRegistrySyncPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * Engine-owned transport for remote configuration and play connections, used only
 * on the server owner thread. Configuration participants remain enrolled until play
 * handoff, including after their early task finishes, so live transactions cannot
 * skip the interval between catch-up and login. Integrated hosts share registries.
 */
final class NeoForgeDynamicSyncTransport implements DynamicSyncTransport {
    interface ConfigurationConnection {
        String participantId();
        boolean connected();
        void send(DynamicSyncMessage message);
        void finishTask();
        void disconnect();
    }

    private static final class Pending {
        final ConfigurationConnection connection;
        DynamicSyncMessage.Kind awaitingKind;
        long generation = -1;
        long deadline;
        boolean taskFinished;
        boolean failed;

        Pending(ConfigurationConnection connection) {
            this.connection = connection;
        }
    }

    private final Supplier<List<String>> playParticipants;
    private final BiConsumer<String, DynamicSyncMessage> playDelivery;
    private final Map<String, Pending> configuring = new LinkedHashMap<>();

    NeoForgeDynamicSyncTransport(MinecraftServer server) {
        this(() -> server.getPlayerList().getPlayers().stream()
                        .filter(player -> !server.isSingleplayerOwner(player.nameAndId()))
                        .map(ServerPlayer::getStringUUID).toList(),
                (id, message) -> {
                    ServerPlayer player = server.getPlayerList().getPlayer(UUID.fromString(id));
                    if (player != null) {
                        PacketDistributor.sendToPlayer(player, new DynamicRegistrySyncPacket(
                                DynamicSyncWireCodec.encodeServerMessage(message)));
                    }
                });
    }

    NeoForgeDynamicSyncTransport(Supplier<List<String>> playParticipants,
            BiConsumer<String, DynamicSyncMessage> playDelivery) {
        this.playParticipants = playParticipants;
        this.playDelivery = playDelivery;
    }

    void joinConfiguration(ConfigurationConnection connection, DynamicRegistryTransactionCoordinator engine) {
        Pending previous = configuring.remove(connection.participantId());
        if (previous != null) {
            previous.connection.disconnect();
            engine.onParticipantLeft(connection.participantId());
        }
        Pending pending = new Pending(connection);
        configuring.put(connection.participantId(), pending);
        engine.onParticipantJoined(connection.participantId());
        if (pending.awaitingKind == null) {
            finish(pending); // Empty activated state needs no remote activation.
        }
    }

    /** Transfers the same participant without a synthetic leave or duplicate catch-up. */
    boolean handoff(String participantId) {
        return configuring.remove(participantId) != null;
    }

    @Override
    public List<String> participants() {
        var ids = new LinkedHashSet<>(playParticipants.get());
        configuring.forEach((id, pending) -> {
            if (!pending.failed && pending.connection.connected()) {
                ids.add(id);
            }
        });
        return List.copyOf(ids);
    }

    @Override
    public void send(String participantId, DynamicSyncMessage message) {
        Pending pending = configuring.get(participantId);
        try {
            if (pending == null) {
                playDelivery.accept(participantId, message);
            } else if (!pending.failed && pending.connection.connected()) {
                pending.generation = message.generation();
                pending.awaitingKind = message.kind();
                pending.deadline = System.currentTimeMillis() + DynamicRegistrySyncWire.ACK_TIMEOUT_MILLIS;
                pending.connection.send(message);
                if (message.kind() == DynamicSyncMessage.Kind.ABORT) {
                    fail(pending, "transaction-aborted");
                }
            }
        } catch (Exception e) {
            NekoJS.LOGGER.warn("[NEKO-3012] Dynamic registry delivery failed; participant {} missed {} generation {}",
                    participantId, message.kind(), message.generation(), e);
            if (pending != null) {
                fail(pending, "delivery-failed");
            }
        }
    }

    @Override
    public void broadcast(DynamicSyncMessage message) {
        for (String id : participants()) {
            send(id, message);
        }
    }

    /** Process after the coordinator: an ACK can synchronously start another batch. */
    void onReply(String participantId, DynamicSyncReply reply) {
        Pending pending = configuring.get(participantId);
        if (pending == null || pending.failed || pending.generation != reply.generation()) {
            return;
        }
        if (reply.kind() == DynamicSyncReply.Kind.ACK) {
            if (!Boolean.TRUE.equals(reply.accepted())) {
                fail(pending, "client-rejected");
            } else if (pending.awaitingKind == DynamicSyncMessage.Kind.STATE_SYNC) {
                pending.awaitingKind = null;
                finish(pending); // STATE_SYNC ACK follows actual client activation.
            }
        } else if (!Boolean.TRUE.equals(reply.activated())) {
            fail(pending, "client-activation-failed");
        } else if (pending.awaitingKind == DynamicSyncMessage.Kind.COMMIT) {
            pending.awaitingKind = null;
            finish(pending);
        }
    }

    /** Defer cleanup out of send/broadcast to avoid re-entering a coordinator decision. */
    void tick(DynamicRegistryTransactionCoordinator engine, long nowMillis) {
        for (var entry : new ArrayList<>(configuring.entrySet())) {
            Pending pending = entry.getValue();
            if (!pending.failed && pending.awaitingKind != null && nowMillis >= pending.deadline) {
                fail(pending, "configuration-timeout");
            }
            if (pending.failed || !pending.connection.connected()) {
                configuring.remove(entry.getKey());
                engine.onParticipantLeft(entry.getKey());
            }
        }
    }

    private void finish(Pending pending) {
        if (!pending.taskFinished && !pending.failed) {
            pending.taskFinished = true;
            pending.connection.finishTask();
            NekoJS.LOGGER.info("Dynamic registry configuration synchronized for {} at generation {}",
                    pending.connection.participantId(), pending.generation);
        }
    }

    private void fail(Pending pending, String reason) {
        if (!pending.failed) {
            pending.failed = true;
            NekoJS.LOGGER.warn("[NEKO-3013] Dynamic registry configuration failed; disconnecting {} at generation {}: {}",
                    pending.connection.participantId(), pending.generation, reason);
            pending.connection.disconnect();
        }
    }

    @Override
    public void close() {
        for (Pending pending : configuring.values()) {
            fail(pending, "activation-owner-closed");
        }
        configuring.clear();
    }
}
//?}
//?}
