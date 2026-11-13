package com.tkisor.nekojs.fabric;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.core.lifecycle.NekoRuntimeRoot;
import com.tkisor.nekojs.core.pack.sync.PackSyncClient;
import com.tkisor.nekojs.core.pack.sync.PackSyncServer;
import com.tkisor.nekojs.core.pack.sync.SyncedPack;
import com.tkisor.nekojs.fabric.mixin.ServerCommonPacketListenerAccessor;
import com.tkisor.nekojs.network.PackBundlePayload;
import com.tkisor.nekojs.network.PackHashListPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.network.Connection;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.function.Consumer;

/**
 * Fabric bridge for pack distribution: the payload classes and the shared core pipeline
 * ({@link PackSyncClient}/{@link PackSyncServer}) come from the same source as NeoForge, so the wire
 * format matches.
 *
 * <p>Server push side: Fabric has no NeoForge {@code RegisterConfigurationTasksEvent}, so the
 * equivalent hook is {@code ServerConfigurationConnectionEvents.CONFIGURE} (vanilla configuration
 * phase, with the peer's channels already negotiated). There {@code addTask} queues a configuration
 * task with the same type id as NeoForge, which behaves the same way: the task runs in the
 * configuration queue and registry sync follows it. Before pushing, {@code canSend} checks that the
 * peer has NekoJS.
 *
 * <p>Client receive side: the Fabric configuration receiver runs on the netty event loop, the same
 * as NeoForge. It reuses the main-thread latch protocol of {@code PackSyncClient} to block the
 * network thread until the files are on disk and the signature is verified, so the remote script
 * output is in place before vanilla validates the registry. An untrusted or failed signature
 * disconnects through {@code Connection.disconnect} and shows the reason.
 */
public final class FabricPackSync {

    private static final Logger LOGGER = LoggerFactory.getLogger("NekoJS-Fabric");

    private FabricPackSync() {}

    /** Server side: payload type registration plus the CONFIGURE configuration task (called from common
     * init). */
    public static void registerServer() {
        PayloadTypeRegistry.clientboundConfiguration().register(
                PackHashListPayload.TYPE, PackHashListPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundConfiguration().register(
                PackBundlePayload.TYPE, PackBundlePayload.STREAM_CODEC);
        ServerConfigurationConnectionEvents.CONFIGURE.register((handler, server) -> {
            if (!PackSyncServer.enabled()) return;
            // In-memory connections (the client of a single-player or LAN host) do not take part in
            // distribution, using the same test as NeoForge PackSyncConfigurationTask#register;
            // otherwise every world join would recompute the hash of every pack for nothing
            if (((ServerCommonPacketListenerAccessor) handler).nekojs$connection().isMemoryConnection()) return;
            if (!ServerConfigurationNetworking.canSend(handler, PackHashListPayload.TYPE)) return;
            handler.addTask(new PushTask(handler));
        });
    }

    /**
     * Configuration-phase push task with the same type id as NeoForge
     * {@code PackSyncConfigurationTask} ({@link PackSyncServer#TASK_ID}) and the same order: the hash
     * list first, then the bundle immediately after it when the run is not hashOnly and there are
     * packs. The task runs in the queue, so client registry validation always happens after it
     * finishes.
     */
    private record PushTask(ServerConfigurationPacketListenerImpl handler) implements ConfigurationTask {

        static final ConfigurationTask.Type TYPE = new ConfigurationTask.Type(PackSyncServer.TASK_ID);

        @Override
        public void start(Consumer<Packet<?>> sender) {
            try {
                List<SyncedPack> packs = PackSyncServer.collectSyncPacks();
                PackHashListPayload hashes = PackHashListPayload.of(packs);
                ServerConfigurationNetworking.send(handler, hashes);
                if (PackSyncServer.shouldSendBundle(packs.size())) {
                    ServerConfigurationNetworking.send(handler, PackBundlePayload.of(packs));
                }
                LOGGER.info("Pushed {} script pack(s) to a configuring client", hashes.entries().size());
            } catch (Exception e) {
                LOGGER.error("Failed to push script pack sync during configuration", e);
            } finally {
                handler.completeTask(TYPE);
            }
        }

        @Override
        public ConfigurationTask.Type type() {
            return TYPE;
        }
    }

    /** Client side: configuration-phase receivers, unload on disconnect, and the CLIENT reload hook
     * (called from client init). */
    public static void registerClient(Runnable refreshPackResources) {
        ClientConfigurationNetworking.registerGlobalReceiver(PackHashListPayload.TYPE,
                (payload, context) -> handleHashList(payload, context, refreshPackResources));
        ClientConfigurationNetworking.registerGlobalReceiver(PackBundlePayload.TYPE,
                (payload, context) -> handleBundle(payload, context, refreshPackResources));
        PackSyncClient.installClientReloadHook(() -> {
            boolean accepted = reloadClientScripts();
            if (accepted) refreshPackResources.run();
            return accepted;
        });
        // Both phases must unload the remote packs: an untrusted pack or a failed signature kicks the
        // client during the configuration phase, so it never reaches the play-phase disconnect
        ClientConfigurationConnectionEvents.DISCONNECT.register((handler, client) -> {
            boolean hadPacks = !com.tkisor.nekojs.core.pack.ScriptPackRegistry.get().serverCachePacks().isEmpty();
            PackSyncClient.handleDisconnect(NekoJSFabricMod.runtimeRootOrNull());
            if (hadPacks) refreshPackResources.run();
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            boolean hadPacks = !com.tkisor.nekojs.core.pack.ScriptPackRegistry.get().serverCachePacks().isEmpty();
            PackSyncClient.handleDisconnect(NekoJSFabricMod.runtimeRootOrNull());
            if (hadPacks) refreshPackResources.run();
        });
    }

    /* ================= Client receivers (netty thread) ================= */

    private static void handleHashList(PackHashListPayload payload, ClientConfigurationNetworking.Context context,
                                       Runnable refreshPackResources) {
        Connection connection = context.packetContext().get(PacketContext.CONNECTION);
        if (connection == null || connection.isMemoryConnection()) return;
        String address = PackSyncClient.normalizeRemoteAddress(connection.getRemoteAddress());
        List<PackSyncClient.HashEntry> entries = payload.toClientEntries();
        PackSyncClient.prepareMainThreadWork();
        context.client().execute(() -> {
            try {
                PackSyncClient.Outcome outcome = PackSyncClient.handleHashList(
                        NekoJSFabricMod.runtimeRootOrNull(), address, entries);
                if (outcome.shouldDisconnect()) {
                    // Fatal rollback can already have cleared the registry before disconnect cleanup.
                    refreshPackResources.run();
                    connection.disconnect(new DisconnectionDetails(Component.literal(outcome.disconnect())));
                }
            } finally {
                PackSyncClient.completeMainThreadWork();
            }
        });
        PackSyncClient.awaitMainThreadWork();
    }

    private static void handleBundle(PackBundlePayload payload, ClientConfigurationNetworking.Context context,
                                     Runnable refreshPackResources) {
        Connection connection = context.packetContext().get(PacketContext.CONNECTION);
        if (connection == null || connection.isMemoryConnection()) return;
        List<SyncedPack> packs = payload.toSyncedPacks();
        PackSyncClient.prepareMainThreadWork();
        context.client().execute(() -> {
            try {
                PackSyncClient.Outcome outcome = PackSyncClient.handleBundle(
                        NekoJSFabricMod.runtimeRootOrNull(), packs);
                if (outcome.shouldDisconnect()) {
                    // The registry can be empty even while old remote resources remain mounted.
                    refreshPackResources.run();
                    connection.disconnect(new DisconnectionDetails(Component.literal(outcome.disconnect())));
                }
            } finally {
                PackSyncClient.completeMainThreadWork();
            }
        });
        PackSyncClient.awaitMainThreadWork();
    }

    private static boolean reloadClientScripts() {
        // A production bundle must not be accepted unless the CLIENT reload owner is wired.
        NekoRuntimeRoot root = NekoJSFabricMod.runtimeRootOrNull();
        if (root == null) {
            LOGGER.error("Cannot reload CLIENT scripts after server pack sync: runtime root is unavailable");
            return false;
        }
        // The CLIENT manager may not exist yet (before autoLoadTypes, or in a dedicated server process),
        // and reload would throw
        if (root.scriptManagerOrNull(ScriptType.CLIENT) == null) {
            LOGGER.error("Cannot reload CLIENT scripts after server pack sync: CLIENT manager is unavailable");
            return false;
        }
        try {
            return root.reload(ScriptType.CLIENT).success();
        } catch (Throwable failure) {
            LOGGER.error("CLIENT script reload after server pack sync failed", failure);
            return false;
        }
    }
}
