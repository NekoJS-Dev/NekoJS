package com.tkisor.nekojs.wrapper.pdata;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.api.inject.EntityExtension;
import com.tkisor.nekojs.api.inject.EntityPDataStore;
import net.minecraft.server.level.ServerPlayer;
import com.tkisor.nekojs.network.PDataSyncPacket;
import com.tkisor.nekojs.network.PlayPacketDispatchers;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 实体 {@code pdata} 的服务端→客户端同步服务：
 * 脏标记 + 每 tick 限量 flush（{@value #MAX_SYNCS_PER_TICK} 个），
 * <p>Entity ids are not reset within one server lifetime: removal sends an empty snapshot and
 * keeps a tombstone revision, so a delayed packet from the removed entity cannot resurrect its
 * mirror. The server revision ledger is cleared only when the server stops.
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
        runOnServer(entity, () -> {
            DIRTY_ENTITIES.remove(entity);
            send(entity);
        });
    }

    public static void syncTo(Entity entity, ServerPlayer player) {
        runOnServer(entity, () -> {
            PDataSyncPacket packet = snapshot(entity);
            if (packet != null) PlayPacketDispatchers.get().sendToPlayer(player, packet);
        });
    }

    public static void clearFor(Entity entity, ServerPlayer player) {
        runOnServer(entity, () -> {
            PlayPacketDispatchers.get().sendToPlayer(player, clearSnapshot(entity.getId()));
        });
    }

    public static void copyPlayerData(Entity original, Entity replacement) {
        runOnServer(replacement, () -> {
            CompoundTag tag = EntityPDataStore.getPDataTag(original, EntityExtension.NEKO_PDATA_KEY);
            EntityPDataStore.setPDataTag(replacement, EntityExtension.NEKO_PDATA_KEY, tag.copy());
            markDirty(replacement);
        });
    }

    private static void runOnServer(Entity entity, Runnable action) {
        if (entity.level().isClientSide()) return;
        MinecraftServer server = entity.level().getServer();
        if (server != null && !server.isSameThread()) {
            server.execute(action);
        } else {
            action.run();
        }
    }

    /** 每 server tick 调用：清掉无效脏实体并按 {@value #MAX_SYNCS_PER_TICK} 上限发送。 */
    public static void flush(MinecraftServer server) {
        if (server != null && !server.isSameThread()) {
            server.execute(() -> flush(server));
            return;
        }
        if (DIRTY_ENTITIES.isEmpty()) return;

        DIRTY_ENTITIES.removeIf(entity -> entity == null || entity.isRemoved() || entity.level().isClientSide());
        int sent = 0;
        for (Entity entity : DIRTY_ENTITIES.toArray(Entity[]::new)) {
            if (sent >= MAX_SYNCS_PER_TICK) break;
            DIRTY_ENTITIES.remove(entity);
            send(entity);
            sent++;
        }
    }

    /**
     * entity 离开 level（卸载/移除）时由平台 listener 调用：递增 revision 发空 data 包，
     * 触发跟踪客户端 {@link #acceptClientSync} 清除该 entity 的 mirror（避免 entity id 复用读到旧数据），
     * 并清理该实体的 dirty 状态；revision tombstone 保留到服务器停止。
     */
    public static void onEntityRemoved(Entity entity) {
        runOnServer(entity, () -> {
            DIRTY_ENTITIES.remove(entity);
            PlayPacketDispatchers.get().sendToPlayersTrackingEntityAndSelf(entity,
                    clearSnapshot(entity.getId()));
        });
    }

    /** 客户端 mirror 读取：该实体最新同步到的 pdata（无数据时返回空 tag 的拷贝）。 */
    public static CompoundTag clientMirror(Entity entity) {
        return CLIENT_ENTITY_MIRROR.getOrDefault(entity.getId(), new CompoundTag()).copy();
    }

    /** 按 entity id 读取 mirror（无 Entity 上下文的测试/诊断用；无数据返回空 tag）。 */
    public static CompoundTag clientMirrorById(int entityId) {
        return CLIENT_ENTITY_MIRROR.getOrDefault(entityId, new CompoundTag()).copy();
    }

    /** 该 entity id 当前是否有 mirror 数据（测试/诊断用）。 */
    public static boolean hasPendingClientData(int entityId) {
        return CLIENT_ENTITY_MIRROR.containsKey(entityId);
    }

    /** 客户端收到同步包：按 revision 去重后更新/清除 mirror（空数据 = 清除）。 */
    public static void acceptClientSync(PDataSyncPacket packet) {
        int currentRevision = CLIENT_REVISIONS.getOrDefault(packet.entityId(), -1);
        if (packet.revision() < currentRevision) return;

        CLIENT_REVISIONS.put(packet.entityId(), packet.revision());
        if (packet.data().isEmpty()) {
            CLIENT_ENTITY_MIRROR.remove(packet.entityId());
            // Keep the tombstone revision. A later entity reusing this ID continues
            // the server-side monotonic sequence, so delayed packets cannot resurrect old data.
        } else {
            CLIENT_ENTITY_MIRROR.put(packet.entityId(), packet.data().copy());
        }
    }

    /** Clears server-side dirty and revision state when a server instance stops. */
    public static void resetServerState() {
        DIRTY_ENTITIES.clear();
        SERVER_REVISIONS.clear();
    }

    /** 客户端断线/重连时清空全部 mirror 与 revision 状态。 */
    public static void clearClientMirrors() {
        CLIENT_ENTITY_MIRROR.clear();
        CLIENT_REVISIONS.clear();
    }

    private static PDataSyncPacket snapshot(Entity entity) {
        return snapshot(entity.getId(), EntityPDataStore.getPDataTag(entity, EntityExtension.NEKO_PDATA_KEY));
    }

    private static PDataSyncPacket snapshot(int entityId, CompoundTag data) {
        if (data.toString().length() > MAX_SYNC_TAG_CHARS) {
            NekoJS.LOGGER.warn("[NEKO-3010] Pdata sync skipped: entity={}, limit={}",
                    entityId, MAX_SYNC_TAG_CHARS);
            return null;
        }
        int revision = SERVER_REVISIONS.merge(entityId, 1, Integer::sum);
        return new PDataSyncPacket(entityId, revision, data.copy());
    }

    private static PDataSyncPacket clearSnapshot(int entityId) {
        return snapshot(entityId, new CompoundTag());
    }

    private static void send(Entity entity) {
        PDataSyncPacket packet = snapshot(entity);
        if (packet != null) PlayPacketDispatchers.get().sendToPlayersTrackingEntityAndSelf(entity, packet);
    }
}
