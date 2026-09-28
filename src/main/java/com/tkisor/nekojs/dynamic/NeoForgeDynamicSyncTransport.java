//? if neoforge {
//? if >=26 {
package com.tkisor.nekojs.dynamic;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.core.dynamic.txn.DynamicSyncMessage;
import com.tkisor.nekojs.core.dynamic.txn.DynamicSyncTransport;
import com.tkisor.nekojs.core.dynamic.txn.DynamicSyncWireCodec;
import com.tkisor.nekojs.network.DynamicRegistrySyncPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Ticket 21 platform wiring: the NeoForge implementation of {@link DynamicSyncTransport}
 * — delivers protocol messages to remote participants through the <b>existing</b>
 * play-phase payload channel ({@link DynamicRegistrySyncPacket}, member of the ticket 17
 * register-once channel family); no second channel is registered.
 *
 * <p><b>Participant rule</b> (a platform fact owned at the Adapter/Transport boundary):
 * a remote participant is an online player whose client does not share this JVM's
 * registries ({@code isSingleplayerOwner} excludes the singleplayer/LAN host — the host
 * shares BuiltInRegistries with the server and is excluded from the ack set).
 *
 * <p><b>No-throw contract</b>: delivery to a vanished participant is skipped (the
 * participant-left/ack-timeout paths already define the outcome); platform send failures
 * are caught and logged, never propagated into the coordinator.
 */
final class NeoForgeDynamicSyncTransport implements DynamicSyncTransport {

    private final MinecraftServer server;

    NeoForgeDynamicSyncTransport(MinecraftServer server) {
        this.server = server;
    }

    @Override
    public List<String> participants() {
        List<String> participants = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!server.isSingleplayerOwner(player.nameAndId())) {
                participants.add(player.getStringUUID());
            }
        }
        return participants;
    }

    @Override
    public void send(String participantId, DynamicSyncMessage message) {
        ServerPlayer player = playerOf(participantId);
        if (player == null) {
            NekoJS.LOGGER.debug("DynamicRegistry sync: participant {} vanished before delivery of {} ({});"
                    + " the leave/timeout path owns the outcome", participantId, message.kind(), message.generation());
            return;
        }
        deliver(player, message);
    }

    @Override
    public void broadcast(DynamicSyncMessage message) {
        // Remote participants only: the host player shares the server's registries, and a
        // duplicate COMMIT would only produce a noisy client-side outcome
        // (commit-without-matching-prepare).
        for (String participantId : participants()) {
            ServerPlayer player = playerOf(participantId);
            if (player != null) {
                deliver(player, message);
            }
        }
    }

    private void deliver(ServerPlayer player, DynamicSyncMessage message) {
        try {
            PacketDistributor.sendToPlayer(player,
                    new DynamicRegistrySyncPacket(DynamicSyncWireCodec.encodeServerMessage(message)));
        } catch (Exception e) {
            NekoJS.LOGGER.warn("DynamicRegistry sync delivery of {} (generation {}) to {} failed: {}",
                    message.kind(), message.generation(), player.getStringUUID(),
                    e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
    }

    private ServerPlayer playerOf(String participantId) {
        UUID uuid;
        try {
            uuid = UUID.fromString(participantId);
        } catch (IllegalArgumentException e) {
            return null;
        }
        return server.getPlayerList().getPlayer(uuid);
    }
}
//?}
//?}
