package com.tkisor.nekojs.fabric;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.network.ClientDataSyncPacket;
import com.tkisor.nekojs.network.PlayPacketDispatcher;
import com.tkisor.nekojs.network.PlayPacketDispatchers;
import com.tkisor.nekojs.wrapper.clientdata.ClientDataStore;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * Fabric bridge for play-phase networking: sets up the {@link PlayPacketDispatcher} send side and
 * registers the play payload types (the S2C sync packet and the bidirectional script channel) with
 * their receivers. The payload classes and wire format come from the shared tree and match NeoForge.
 *
 * <p>The explicit Fabric play subset: {@code ClientData} key-value sync ({@code ClientData.sync} to
 * {@code clientData.get}) and the script channel {@code NekoScriptPayload} (bidirectional, delivered
 * neutrally through {@link com.tkisor.nekojs.network.NetworkMessageHandler}). Pdata sync goes
 * through {@link FabricPDataSync} (the Fabric {@code MixinEntity} attaches the entity extension and
 * {@code NekoEntityPDataMixin} provides the storage container), and configuration-phase pack
 * distribution goes through {@link FabricPackSync}. The network pieces deliberately left out (the
 * error panel {@code ShowErrorListPacket} and the script editor/dashboard sync packets) are listed
 * in {@code docs/fabric-port-status.md}; that parity is intentionally not faked.
 */
public final class FabricPlayNetwork {

    /** Sending to the whole server needs a server instance, and script threads have no context, so the
     * current instance is held for the life of the server. */
    private static volatile MinecraftServer currentServer;

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
        ServerLifecycleEvents.SERVER_STARTING.register(server -> currentServer = server);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> currentServer = null);
    }

    /** Client side: receivers, plus clearing the store on disconnect and on world change (called from
     * client init). */
    public static void registerClient() {
        ClientPlayNetworking.registerGlobalReceiver(ClientDataSyncPacket.TYPE, (payload, context) ->
                context.client().execute(() -> acceptClientData(payload)));
        // Script channel client receiver: hops to the client main thread first, then posts the event
        // neutrally
        ClientPlayNetworking.registerGlobalReceiver(com.tkisor.nekojs.network.NekoScriptPayload.TYPE,
                (payload, context) -> context.client().execute(() ->
                        com.tkisor.nekojs.network.NetworkMessageHandler.postClientEvent(payload)));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientDataStore.SHARED.clear());
        // NeoForge hooks client level unload, which clears on both disconnect and dimension change; Fabric
        // has no matching event, so watching the client level instance is equivalent. It clears only
        // when leaving an existing world (the previous level is non-null); the null-to-level change on
        // first join does not clear, or it would wipe the data just pushed down on join. The rule lives
        // in ClientLevelWatch
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (LEVEL_WATCH.leftPreviousLevel(client.level)) {
                ClientDataStore.SHARED.clear();
            }
        });
    }

    /**
     * Same as {@code ClientDataMessageHandler} on NeoForge: parses the JSON on the main thread,
     * then writes it into the shared store.
     */
    private static void acceptClientData(ClientDataSyncPacket payload) {
        try {
            JsonElement json = JsonParser.parseString(payload.json());
            ClientDataStore.SHARED.accept(payload.key(), json);
        } catch (IllegalArgumentException ignored) {
            // Invalid key (empty or too long). The sending side validates it, so a bad packet is just dropped.
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
