package com.tkisor.nekojs.fabric;

import com.tkisor.nekojs.api.inject.EntityPDataStore;
import com.tkisor.nekojs.api.inject.NekoEntityPData;
import com.tkisor.nekojs.network.PDataSyncPacket;
import com.tkisor.nekojs.wrapper.pdata.PDataSyncService;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;

/**
 * The Fabric side of pdata:
 * <ul>
 *   <li>Storage: {@code NekoEntityPDataMixin} adds {@code neko$pdataTag} to {@code Entity} (through
 *       the duck interface {@link NekoEntityPData}), and the tag is stored in
 *       {@link EntityPDataStore}; both platforms read and write the same shape;</li>
 *   <li>Sync: registers the S2C {@code PDataSyncPacket} and its client receiver (matching NeoForge
 *       {@code NekoJSNetwork#handlePDataSyncOnClient}), flushes every server tick, and clears the
 *       revision when an entity changes world using the "left the tracking range" rule
 *       ({@code onEntityRemoved}, which stops a reused entity id from reading stale data).</li>
 * </ul>
 */
public final class FabricPDataSync {

    /** findEntity needs a server instance (same lifetime as FabricPlayNetwork). */
    private static volatile MinecraftServer currentServer;

    /** Clears only when the client leaves an old world; the null-to-level change on first join does not
     * clear. See {@link ClientLevelWatch}. */
    private static final ClientLevelWatch LEVEL_WATCH = new ClientLevelWatch();

    private FabricPDataSync() {}

    /** Server side: payload registration, store setup, and the flush and entity-world-change hooks
     * (called from common init). */
    public static void registerServer() {
        PayloadTypeRegistry.clientboundPlay().register(
                PDataSyncPacket.TYPE, PDataSyncPacket.STREAM_CODEC);
        EntityPDataStore.install(fullAccess());
        EntityTrackingEvents.START_TRACKING.register((entity, player) -> PDataSyncService.syncTo(entity, player));
        EntityTrackingEvents.STOP_TRACKING.register((entity, player) -> PDataSyncService.clearFor(entity, player));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> PDataSyncService.markDirty(handler.player));
        ServerPlayerEvents.COPY_FROM.register((original, replacement, alive) ->
                PDataSyncService.copyPlayerData(original, replacement));
        ServerPlayerEvents.AFTER_RESPAWN.register((original, replacement, alive) ->
                PDataSyncService.markDirty(replacement));
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            currentServer = server;
            PDataSyncService.resetServerState();
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            currentServer = null;
            PDataSyncService.resetServerState();
        });
        ServerTickEvents.END_SERVER_TICK.register(FabricPDataSync::flush);
        // Matches NeoForge's EntityLeaveLevelEvent semantics (chunk unload, death, despawn and dimension
        // change all count as leaving). Using only the dimension change event would miss despawned
        // entities, leaving revision and mirror data behind so that a reused entity id reads stale data
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof net.minecraft.server.level.ServerPlayer) {
                PDataSyncService.markDirty(entity);
            }
        });
        ServerEntityEvents.ENTITY_UNLOAD.register(
                (entity, level) -> PDataSyncService.onEntityRemoved(entity));
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

            // The entity reference side dereferences the mixin container directly: inside the
            // EntityJoinLevelEvent window the entity is not yet in the level entity index, so a
            // findEntity(id) lookup is always empty and the write is silently dropped
            @Override
            public CompoundTag get(Entity entity, String key) {
                CompoundTag container = ((NekoEntityPData) entity).neko$getPDataRoot();
                return container.getCompound(key).orElseGet(CompoundTag::new).copy();
            }

            @Override
            public void set(Entity entity, String key, CompoundTag tag) {
                CompoundTag container = ((NekoEntityPData) entity).neko$getPDataRoot();
                if (tag.isEmpty()) {
                    container.remove(key);
                } else {
                    container.put(key, tag.copy());
                }
            }
        };
    }

    /** Entity id to entity. Called on the server thread only (flush and script reads and writes all run
     * inside the server tick). */
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

    /** Client side: receiver plus mirror clearing on disconnect (called from client init). */
    public static void registerClient() {
        ClientPlayNetworking.registerGlobalReceiver(PDataSyncPacket.TYPE, (payload, context) ->
                context.client().execute(() -> acceptClientSync(payload, context.client().level)));
        ClientPlayConnectionEvents.DISCONNECT.register(
                (handler, client) -> PDataSyncService.clearClientMirrors());
        // A dimension change also clears (NeoForge hooks client level unload). Clear only when leaving an
        // existing world; the null-to-level change on first join does not clear, or it would wipe the
        // data just pushed down on join. The rule lives in ClientLevelWatch
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client ->
                observeClientLevel(client.level));
    }

    private static void acceptClientSync(PDataSyncPacket payload, Object level) {
        observeClientLevel(level);
        PDataSyncService.acceptClientSync(payload);
    }

    private static void observeClientLevel(Object level) {
        if (LEVEL_WATCH.leftPreviousLevel(level)) {
            PDataSyncService.clearClientMirrors();
        }
    }
}
