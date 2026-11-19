package com.tkisor.nekojs.fabric;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.core.stage.ClientStageMirror;
import com.tkisor.nekojs.core.stage.gate.StageWireProtocol;
import com.tkisor.nekojs.fabric.event.FabricServerEventBindings;
import com.tkisor.nekojs.network.ClientDataSyncPacket;
import com.tkisor.nekojs.network.PlayPacketDispatcher;
import com.tkisor.nekojs.network.PlayPacketDispatchers;
import com.tkisor.nekojs.wrapper.clientdata.ClientDataStore;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * Fabric bridge for play-phase networking: sets up the {@link PlayPacketDispatcher} send side and
 * registers the play payload types (the S2C sync packet and the bidirectional script channel) with
 * their receivers. The payload classes and wire format come from the shared tree.
 *
 * <p>The explicit Fabric play subset: {@code ClientData} key-value sync ({@code ClientData.sync} to
 * {@code clientData.get}), reserved stage/gate synchronization over {@link StageWireProtocol#CHANNEL_KEY},
 * and the script channel {@code NekoScriptPayload} (bidirectional, delivered neutrally through
 * {@link com.tkisor.nekojs.network.NetworkMessageHandler}). Pdata sync goes through
 * {@link FabricPDataSync}, and configuration-phase pack distribution goes through
 * {@link FabricPackSync}.
 */
public final class FabricPlayNetwork {

    /** Sending to the whole server needs a server instance, and script threads have no context, so the
     * current instance is held for the life of the server. */
    private static volatile MinecraftServer currentServer;
    private static StageClientSession stageClientSession;

    /** Clears only when the client leaves an old world; the null-to-level change on first join does not
     * clear. See {@link ClientLevelWatch}. */
    private static final ClientLevelWatch LEVEL_WATCH = new ClientLevelWatch();

    private FabricPlayNetwork() {}

    /** Server side: send-side setup plus S2C payload type registration (called from common init). */
    public static void registerServer() {
        PayloadTypeRegistry.clientboundPlay().register(
                ClientDataSyncPacket.TYPE, ClientDataSyncPacket.STREAM_CODEC);
        PlayPacketDispatchers.install(new FabricDispatcher());
        // Script-defined channel (Network.sendToServer/sendToPlayer/sendToAll): types in both directions
        // plus the server receiver. The receiver runs on the network thread, so it hops to the server
        // main thread first and posts the event neutrally (script callbacks that touch MC objects must
        // run on the main thread).
        PayloadTypeRegistry.serverboundPlay().register(
                com.tkisor.nekojs.network.NekoScriptPayload.TYPE, com.tkisor.nekojs.network.NekoScriptPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(
                com.tkisor.nekojs.network.NekoScriptPayload.TYPE, com.tkisor.nekojs.network.NekoScriptPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(com.tkisor.nekojs.network.NekoScriptPayload.TYPE,
                (payload, context) -> context.server().execute(() ->
                        com.tkisor.nekojs.network.NetworkMessageHandler.postServerEvent(payload, context.player())));
        ServerLifecycleEvents.SERVER_STARTING.register(FabricPlayNetwork::bindServer);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            if (!FabricServerEventBindings.isStopDeferred(server)) releaseServer(server);
        });
    }

    private static synchronized void bindServer(MinecraftServer server) {
        currentServer = server;
    }

    public static synchronized void releaseServer(MinecraftServer server) {
        if (currentServer == server) currentServer = null;
    }

    /** Client side: receivers, plus clearing the store and stage mirror on disconnect and world change. */
    public static void registerClient() {
        ClientPlayNetworking.registerGlobalReceiver(ClientDataSyncPacket.TYPE, (payload, context) -> {
            var client = context.client();
            var sender = context.responseSender();
            client.execute(() -> acceptClientData(payload, sender));
        });
        // Script channel client receiver: hops to the client main thread first, then posts the event
        // neutrally
        ClientPlayNetworking.registerGlobalReceiver(com.tkisor.nekojs.network.NekoScriptPayload.TYPE,
                (payload, context) -> context.client().execute(() ->
                        com.tkisor.nekojs.network.NetworkMessageHandler.postClientEvent(payload)));
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (stageClientSession != null && stageClientSession.connection() != handler) clearClientStages();
            stageClientSession = new StageClientSession(handler, sender);
            if (client.player != null && client.player.connection == handler) {
                ClientStageMirror.SHARED.setLocalPlayer(
                        client.player.getStringUUID(), client.player.getScoreboardName());
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ClientDataStore.SHARED.clear();
            client.execute(() -> {
                if (stageClientSession == null || stageClientSession.connection() != handler) return;
                stageClientSession = null;
                clearClientStages();
            });
        });
        // Watching the client level instance clears only when leaving an existing world (the previous
        // level is non-null); the null-to-level change on first join does not clear, so data pushed on
        // join is preserved. The rule lives in ClientLevelWatch.
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (LEVEL_WATCH.leftPreviousLevel(client.level)) {
                ClientDataStore.SHARED.clear();
            }
            if (stageClientSession != null && client.player != null
                    && client.player.connection == stageClientSession.connection()) {
                ClientStageMirror.SHARED.setLocalPlayer(
                        client.player.getStringUUID(), client.player.getScoreboardName());
                if (client.level != null) ClientStageMirror.SHARED.tickClient();
            }
        });
    }

    private record StageClientSession(ClientPacketListener connection, PacketSender sender) {}

    private static void clearClientStages() {
        ClientStageMirror.SHARED.clear();
        StageWireProtocol.clearCachedSummary(ClientStageMirror.SHARED);
    }

    /**
     * Parses incoming {@link ClientDataSyncPacket} payloads on the client main thread, routing
     * reserved stage packets to {@link StageWireProtocol} and script data entries to
     * {@link ClientDataStore#SHARED}.
     */
    private static void acceptClientData(ClientDataSyncPacket payload, PacketSender sender) {
        if (StageWireProtocol.CHANNEL_KEY.equals(payload.key())) {
            if (stageClientSession == null || stageClientSession.sender() != sender) return;
            try {
                StageWireProtocol.applyOnClient(payload.json(), ClientStageMirror.SHARED);
            } catch (Exception e) {
                NekoJS.LOGGER.warn("Discarding malformed client stage sync packet: {}", e.toString());
            }
            return;
        }
        try {
            JsonElement json = JsonParser.parseString(payload.json());
            ClientDataStore.SHARED.accept(payload.key(), json);
        } catch (Exception e) {
            NekoJS.LOGGER.warn("Discarding malformed client data packet for key {}: {}", payload.key(), e.toString());
        }
    }

    private static final class FabricDispatcher implements PlayPacketDispatcher {

        @Override
        public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
            if (ServerPlayNetworking.canSend(player, payload.type())) {
                ServerPlayNetworking.send(player, payload);
            }
        }

        @Override
        public void sendToAllPlayers(CustomPacketPayload payload) {
            MinecraftServer server = currentServer;
            if (server == null) {
                // A failed send must not break the script: when no server is running, drop the payload and warn.
                NekoJS.LOGGER.warn("No server is running; dropping broadcast of {}", payload.type().id());
                return;
            }
            for (ServerPlayer player : PlayerLookup.all(server)) {
                sendToPlayer(player, payload);
            }
        }

        @Override
        public void sendToPlayersTrackingEntityAndSelf(Entity entity, CustomPacketPayload payload) {
            if (!(entity.level() instanceof ServerLevel)) return;
            for (ServerPlayer player : PlayerLookup.tracking(entity)) {
                sendToPlayer(player, payload);
            }
            if (entity instanceof ServerPlayer self) {
                sendToPlayer(self, payload);
            }
        }
    }
}
