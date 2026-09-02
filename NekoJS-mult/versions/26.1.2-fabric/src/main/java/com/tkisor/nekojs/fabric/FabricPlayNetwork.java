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
 * play 阶段网络的 fabric 桥：装配 {@link PlayPacketDispatcher} 发送面，
 * 注册 S2C payload 类型与客户端 receiver。payload 类与线格式沿用共享树，与 NeoForge 侧一致。
 *
 * <p>当前带 {@code ClientData} 键值同步（{@code ClientData.sync} → {@code clientData.get}）；
 * pdata 同步走 {@link FabricPDataSync}（实体扩展由 fabric {@code MixinEntity} 挂接、
 * 持久化容器由 {@code NekoEntityPDataMixin} 提供）。尚未接的 play 网络面（脚本编辑器同步
 * 8 包、NekoScriptPayload 自定义通道）见 {@code docs/fabric-port-status.md}。
 */
public final class FabricPlayNetwork {

    /** 发全服需要 server 实例，而脚本线程没有上下文——跟随服务器生命周期持有当前实例。 */
    private static volatile MinecraftServer currentServer;

    /** 上一次见到的客户端世界实例（切维度/断线时变化，用于清空 clientData）。 */
    private static Object lastClientLevel;

    private FabricPlayNetwork() {}

    /** 服务器半：发送面装配 + S2C payload 类型注册（common init 调用）。 */
    public static void registerServer() {
        PayloadTypeRegistry.clientboundPlay().register(
                ClientDataSyncPacket.TYPE, ClientDataSyncPacket.STREAM_CODEC);
        PlayPacketDispatchers.install(new FabricDispatcher());
        // 脚本自定义通道（Network.sendToServer/sendToPlayer/sendToAll）：双向类型 + 服务端 receiver。
        // receiver 在网络线程触发——切服务端主线程后走中立投递（脚本回调访问 MC 对象须主线程）。
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

    /** 客户端半：receiver + 断线/切世界清空 store（client init 调用）。 */
    public static void registerClient() {
        ClientPlayNetworking.registerGlobalReceiver(ClientDataSyncPacket.TYPE, (payload, context) ->
                context.client().execute(() -> acceptClientData(payload)));
        // 脚本自定义通道客户端 receiver：切客户端主线程后走中立投递
        ClientPlayNetworking.registerGlobalReceiver(com.tkisor.nekojs.network.NekoScriptPayload.TYPE,
                (payload, context) -> context.client().execute(() ->
                        com.tkisor.nekojs.network.NetworkMessageHandler.postClientEvent(payload)));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientDataStore.SHARED.clear());
        // NeoForge 侧挂在 client level unload（断线与切维度都清），fabric 无对应事件——
        // 盯客户端世界实例变化等价：切维度换 ClientLevel 实例、断线变 null。
        // 只在"离开一个已有世界"时清（lastClientLevel 非空），进服那次 null→世界不清，
        // 否则会把刚随进服推下来的数据一起抹掉。
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            Object level = client.level;
            if (level == lastClientLevel) return;
            boolean leftPreviousLevel = lastClientLevel != null;
            lastClientLevel = level;
            if (leftPreviousLevel) {
                ClientDataStore.SHARED.clear();
            }
        });
    }

    /** 与 NeoForge 侧 {@code ClientDataMessageHandler} 同：主线程解析 JSON 后写入共享 store。 */
    private static void acceptClientData(ClientDataSyncPacket payload) {
        try {
            JsonElement json = JsonParser.parseString(payload.json());
            ClientDataStore.SHARED.accept(payload.key(), json);
        } catch (IllegalArgumentException ignored) {
            // key 非法（空/超长）——构造端已校验，坏包直接丢弃
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
                // 契约是发送失败不打断脚本：服务器未运行时丢弃并告警
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
