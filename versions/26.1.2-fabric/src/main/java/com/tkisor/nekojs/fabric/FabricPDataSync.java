package com.tkisor.nekojs.fabric;

import com.tkisor.nekojs.api.inject.EntityPDataStore;
import com.tkisor.nekojs.api.inject.NekoEntityPData;
import com.tkisor.nekojs.network.PDataSyncPacket;
import com.tkisor.nekojs.wrapper.pdata.PDataSyncService;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;

/**
 * pdata 的 fabric 面（票 09）：
 * <ul>
 *   <li>持久化容器：{@code NekoEntityPDataMixin} 给 {@code Entity} 加的 {@code neko$pdataTag}
 *       （duck 接口 {@link NekoEntityPData}）→ 装进 {@link EntityPDataStore}，两平台同形读写；</li>
 *   <li>同步：S2C {@code PDataSyncPacket} 注册 + 客户端 receiver（对齐 NeoForge
 *       {@code NekoJSNetwork#handlePDataSyncOnClient}），每 server tick flush、
 *       实体换世界时按「离开追踪范围」语义清 revision（{@code onEntityRemoved}，
 *       entity id 复用防线）。</li>
 * </ul>
 */
public final class FabricPDataSync {

    /** findEntity 需要 server 实例（与 FabricPlayNetwork 同生命周期）。 */
    private static volatile MinecraftServer currentServer;

    private FabricPDataSync() {}

    /** 服务器半：payload 注册 + store 装配 + flush / 实体换世界钩子（common init 调用）。 */
    public static void registerServer() {
        PayloadTypeRegistry.clientboundPlay().register(
                PDataSyncPacket.TYPE, PDataSyncPacket.STREAM_CODEC);
        EntityPDataStore.install(fullAccess());
        ServerLifecycleEvents.SERVER_STARTING.register(server -> currentServer = server);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> currentServer = null);
        ServerTickEvents.END_SERVER_TICK.register(FabricPDataSync::flush);
        ServerEntityLevelChangeEvents.AFTER_ENTITY_CHANGE_LEVEL.register(
                (original, newEntity, origin, destination) -> PDataSyncService.onEntityRemoved(original));
    }

    private static EntityPDataStore.Access fullAccess() {
        return new EntityPDataStore.Access() {
            @Override
            public CompoundTag get(int entityId, String key) {
                Entity entity = findEntity(entityId);
                if (entity == null) return new CompoundTag();
                CompoundTag container = ((NekoEntityPData) entity).neko$getPDataRoot();
                return container.getCompound(key).orElseGet(CompoundTag::new).copy();
            }

            @Override
            public void set(int entityId, String key, CompoundTag tag) {
                Entity entity = findEntity(entityId);
                if (entity == null) return;
                CompoundTag container = ((NekoEntityPData) entity).neko$getPDataRoot();
                if (tag.isEmpty()) {
                    container.remove(key);
                } else {
                    container.put(key, tag.copy());
                }
            }
        };
    }

    /** 实体 id → 实体：仅服务器线程调用（flush / 脚本读写都在服务器 tick 内）。 */
    private static Entity findEntity(int entityId) {
        var server = currentServer;
        if (server == null) return null;
        for (var level : server.getAllLevels()) {
            Entity entity = level.getEntity(entityId);
            if (entity != null) return entity;
        }
        return null;
    }

    private static void flush(MinecraftServer server) {
        PDataSyncService.flush(server);
    }

    /** 客户端半：receiver + 断线清 mirror（client init 调用）。 */
    public static void registerClient() {
        ClientPlayNetworking.registerGlobalReceiver(PDataSyncPacket.TYPE, (payload, context) ->
                context.client().execute(() -> PDataSyncService.acceptClientSync(payload)));
        ClientPlayConnectionEvents.DISCONNECT.register(
                (handler, client) -> PDataSyncService.clearClientMirrors());
    }
}
