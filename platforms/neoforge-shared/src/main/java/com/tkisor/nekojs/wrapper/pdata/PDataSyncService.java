package com.tkisor.nekojs.wrapper.pdata;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.api.inject.EntityExtension;
import com.tkisor.nekojs.network.PDataSyncPacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 实体 {@code pdata} 的服务端→客户端同步服务：
 * 脏标记 + 每 tick 限量 flush（{@value #MAX_SYNCS_PER_TICK} 个），
 * 客户端按 entity id + revision 维护 mirror，超限（{@value #MAX_SYNC_TAG_CHARS} 字符）的数据跳过并告警。
 */
public final class PDataSyncService {
    private static final int MAX_SYNCS_PER_TICK = 256;
    private static final int MAX_SYNC_TAG_CHARS = 32768;
    // IdentityHashMap-backed for entity-identity semantics; synchronized because
    // markDirty (JS/timer thread) and flush (server tick thread) can overlap.
    private static final Set<Entity> DIRTY_ENTITIES = Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<>()));
    private static final Map<Integer, Integer> SERVER_REVISIONS = new ConcurrentHashMap<>();
    private static final Map<Integer, CompoundTag> CLIENT_ENTITY_MIRROR = new ConcurrentHashMap<>();
    private static final Map<Integer, Integer> CLIENT_REVISIONS = new ConcurrentHashMap<>();

    private PDataSyncService() {}

    /** 标记实体 pdata 已变更（服务端；下一个 server tick flush 时同步）。 */
    public static void markDirty(Entity entity) {
        if (!entity.level().isClientSide()) DIRTY_ENTITIES.add(entity);
    }

    /** 立即同步该实体的 pdata（绕过脏标记队列；仅服务端）。 */
    public static void syncNow(Entity entity) {
        if (entity.level().isClientSide()) return;
        send(entity);
        DIRTY_ENTITIES.remove(entity);
    }

    /** 每 server tick 调用：清掉无效脏实体并按 {@value #MAX_SYNCS_PER_TICK} 上限发送。 */
    public static void flush(MinecraftServer server) {
        if (DIRTY_ENTITIES.isEmpty()) return;

        DIRTY_ENTITIES.removeIf(entity -> entity == null || entity.isRemoved() || entity.level().isClientSide());
        int sent = 0;
        for (Entity entity : DIRTY_ENTITIES.toArray(Entity[]::new)) {
            if (sent >= MAX_SYNCS_PER_TICK) break;
            send(entity);
            DIRTY_ENTITIES.remove(entity);
            sent++;
        }
    }

    /**
     * entity 离开 level（卸载/移除）时由平台 listener 调用：递增 revision 发空 data 包，
     * 触发跟踪客户端 {@link #acceptClientSync} 清除该 entity 的 mirror（避免 entity id 复用读到旧数据），
     * 并清理由此 entity 占用的 server 端状态。
     */
    public static void onEntityRemoved(Entity entity) {
        if (entity.level().isClientSide()) return;
        int id = entity.getId();
        int revision = SERVER_REVISIONS.merge(id, 1, Integer::sum);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity, new PDataSyncPacket(id, revision, new CompoundTag()));
        SERVER_REVISIONS.remove(id);
        DIRTY_ENTITIES.remove(entity);
    }

    /** 客户端 mirror 读取：该实体最新同步到的 pdata（无数据时返回空 tag 的拷贝）。 */
    public static CompoundTag clientMirror(Entity entity) {
        return CLIENT_ENTITY_MIRROR.getOrDefault(entity.getId(), new CompoundTag()).copy();
    }

    /** 客户端收到同步包：按 revision 去重后更新/清除 mirror（空数据 = 清除）。 */
    public static void acceptClientSync(PDataSyncPacket packet) {
        int currentRevision = CLIENT_REVISIONS.getOrDefault(packet.entityId(), -1);
        if (packet.revision() < currentRevision) return;

        CLIENT_REVISIONS.put(packet.entityId(), packet.revision());
        if (packet.data().isEmpty()) {
            CLIENT_ENTITY_MIRROR.remove(packet.entityId());
        } else {
            CLIENT_ENTITY_MIRROR.put(packet.entityId(), packet.data().copy());
        }
    }

    /** 客户端断线/重连时清空全部 mirror 与 revision 状态。 */
    public static void clearClientMirrors() {
        CLIENT_ENTITY_MIRROR.clear();
        CLIENT_REVISIONS.clear();
    }

    private static void send(Entity entity) {
        CompoundTag data = ((EntityExtension) entity).neko$pdata().copyTag();
        if (data.toString().length() > MAX_SYNC_TAG_CHARS) {
            NekoJS.LOGGER.warn("Skipping oversized pdata sync for entity {} ({})", entity.getId(), entity.getType());
            return;
        }

        int revision = SERVER_REVISIONS.merge(entity.getId(), 1, Integer::sum);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity, new PDataSyncPacket(entity.getId(), revision, data));
    }
}
