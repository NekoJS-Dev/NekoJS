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

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.List;
import java.util.function.Consumer;

/**
 * 包分发的 fabric 桥：payload 类与共享核管线（{@link PackSyncClient}/
 * {@link PackSyncServer}）与 NeoForge 侧同源，线格式一致。
 *
 * <p>服务器推送面：fabric 没有 NeoForge 的 {@code RegisterConfigurationTasksEvent}，等价入口是
 * {@code ServerConfigurationConnectionEvents.CONFIGURE}（vanilla 配置期，对端通道已协商）——
 * 在其中 {@code addTask} 挂一个与 NeoForge 侧同 Type id 的配置任务，语义完全对齐：
 * 任务在配置队列里执行，注册表同步排在其后。推送前以 {@code canSend} 校验对端装了 NekoJS。
 *
 * <p>客户端接收面：fabric configuration receiver 在 netty event loop 执行，与 NeoForge 侧
 * 同构——复用 {@code PackSyncClient} 的主线程 latch 协议阻塞网络线程直到落盘/验签完成，
 * 保证原版注册表校验时远端脚本产物已就位；未信任/验签失败经 {@code Connection.disconnect}
 * 断连并展示原因。
 */
public final class FabricPackSync {

    private static final Logger LOGGER = LoggerFactory.getLogger("NekoJS-Fabric");

    private FabricPackSync() {}

    /** 服务器半：payload 类型注册 + CONFIGURE 挂配置任务（common init 调用）。 */
    public static void registerServer() {
        PayloadTypeRegistry.clientboundConfiguration().register(
                PackHashListPayload.TYPE, PackHashListPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundConfiguration().register(
                PackBundlePayload.TYPE, PackBundlePayload.STREAM_CODEC);
        ServerConfigurationConnectionEvents.CONFIGURE.register((handler, server) -> {
            if (!PackSyncServer.enabled()) return;
            // 内存连接（单人 / 局域网主机自己的客户端）不参与分发，与 NeoForge 侧
            // PackSyncConfigurationTask#register 同判据——否则每次进世界都白算一遍全部包的哈希
            if (((ServerCommonPacketListenerAccessor) handler).nekojs$connection().isMemoryConnection()) return;
            if (!ServerConfigurationNetworking.canSend(handler, PackHashListPayload.TYPE)) return;
            handler.addTask(new PushTask(handler));
        });
    }

    /**
     * 配置阶段推送任务：与 NeoForge 侧 {@code PackSyncConfigurationTask} 同 Type id
     * （{@link PackSyncServer#TASK_ID}）与同序——先哈希清单，非 hashOnly 且有包时紧随 bundle。
     * 任务在队列里执行，客户端注册表校验必然晚于本任务完成。
     */
    private record PushTask(ServerConfigurationPacketListenerImpl handler) implements ConfigurationTask {

        static final ConfigurationTask.Type TYPE = new ConfigurationTask.Type(PackSyncServer.TASK_ID);

        @Override
        public void start(Consumer<Packet<?>> sender) {
            try {
                List<SyncedPack> packs = PackSyncServer.collectSyncPacks();
                PackHashListPayload hashes = PackHashListPayload.of(packs);
                ServerConfigurationNetworking.send(handler, hashes);
                if (!PackSyncServer.hashOnly() && !hashes.entries().isEmpty()) {
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

    /** 客户端半：配置阶段 receiver + 断线卸载 + CLIENT 重载钩子（client init 调用）。 */
    public static void registerClient() {
        ClientConfigurationNetworking.registerGlobalReceiver(PackHashListPayload.TYPE, FabricPackSync::handleHashList);
        ClientConfigurationNetworking.registerGlobalReceiver(PackBundlePayload.TYPE, FabricPackSync::handleBundle);
        PackSyncClient.installClientReloadHook(FabricPackSync::reloadClientScripts);
        // 两个阶段都要卸载远端包：未信任/验签失败是在配置阶段就被踢，走不到 play 阶段断线
        ClientConfigurationConnectionEvents.DISCONNECT.register((handler, client) ->
                PackSyncClient.handleDisconnect(NekoJSFabricMod.runtimeRootOrNull()));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
                PackSyncClient.handleDisconnect(NekoJSFabricMod.runtimeRootOrNull()));
    }

    /* ================= 客户端 receiver（netty 线程） ================= */

    private static void handleHashList(PackHashListPayload payload, ClientConfigurationNetworking.Context context) {
        Connection connection = context.packetContext().get(PacketContext.CONNECTION);
        if (connection == null || connection.isMemoryConnection()) return;
        String address = resolveServerAddress(connection);
        List<PackSyncClient.HashEntry> entries = payload.toClientEntries();
        PackSyncClient.prepareMainThreadWork();
        context.client().execute(() -> {
            try {
                PackSyncClient.handleHashList(NekoJSFabricMod.runtimeRootOrNull(), address, entries);
            } finally {
                PackSyncClient.completeMainThreadWork();
            }
        });
        PackSyncClient.awaitMainThreadWork();
    }

    private static void handleBundle(PackBundlePayload payload, ClientConfigurationNetworking.Context context) {
        Connection connection = context.packetContext().get(PacketContext.CONNECTION);
        if (connection == null || connection.isMemoryConnection()) return;
        List<SyncedPack> packs = payload.toSyncedPacks();
        PackSyncClient.prepareMainThreadWork();
        context.client().execute(() -> {
            try {
                PackSyncClient.Outcome outcome = PackSyncClient.handleBundle(
                        NekoJSFabricMod.runtimeRootOrNull(), packs);
                if (outcome.shouldDisconnect()) {
                    connection.disconnect(new DisconnectionDetails(Component.literal(outcome.disconnect())));
                }
            } finally {
                PackSyncClient.completeMainThreadWork();
            }
        });
        PackSyncClient.awaitMainThreadWork();
    }

    private static boolean reloadClientScripts() {
        // root 经 loader entry 的 package-private accessor 获取（原 null 判定语义保留）
        NekoRuntimeRoot root = NekoJSFabricMod.runtimeRootOrNull();
        if (root == null) return true;
        // CLIENT 管理器可能尚未建立（autoLoadTypes 之前 / 专用服务器进程）——reload 会抛
        if (root.scriptManagerOrNull(ScriptType.CLIENT) == null) return true;
        try {
            return root.reload(ScriptType.CLIENT).success();
        } catch (Throwable failure) {
            LOGGER.error("CLIENT script reload after server pack sync failed", failure);
            return false;
        }
    }

    /** 配置阶段 {@code Minecraft#getCurrentServer()} 未就绪，从连接远端地址取 bucket 所用地址。 */
    private static String resolveServerAddress(Connection connection) {
        SocketAddress remote = connection.getRemoteAddress();
        if (remote instanceof InetSocketAddress isa) {
            String host = isa.getHostString();
            if (host != null && !host.isBlank()) {
                return host.trim().toLowerCase();
            }
        }
        return remote != null ? remote.toString() : "unknown";
    }
}
