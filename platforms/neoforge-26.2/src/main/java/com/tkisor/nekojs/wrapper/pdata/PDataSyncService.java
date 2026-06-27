package com.tkisor.nekojs.wrapper.pdata;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.api.inject.EntityExtension;
import com.tkisor.nekojs.network.PDataSyncPacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

public final class PDataSyncService {
    private static final int MAX_SYNCS_PER_TICK = 256;
    private static final int MAX_SYNC_TAG_CHARS = 32768;
    private static final Set<Entity> DIRTY_ENTITIES = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Map<Integer, Integer> SERVER_REVISIONS = new HashMap<>();
    private static final Map<Integer, CompoundTag> CLIENT_ENTITY_MIRROR = new HashMap<>();
    private static final Map<Integer, Integer> CLIENT_REVISIONS = new HashMap<>();

    private PDataSyncService() {}

    public static void markDirty(Entity entity) {
        if (!entity.level().isClientSide()) DIRTY_ENTITIES.add(entity);
    }

    public static void syncNow(Entity entity) {
        if (entity.level().isClientSide()) return;
        send(entity);
        DIRTY_ENTITIES.remove(entity);
    }

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

    public static CompoundTag clientMirror(Entity entity) {
        return CLIENT_ENTITY_MIRROR.getOrDefault(entity.getId(), new CompoundTag()).copy();
    }

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
