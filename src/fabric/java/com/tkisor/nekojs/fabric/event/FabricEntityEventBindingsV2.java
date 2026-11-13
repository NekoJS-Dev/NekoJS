package com.tkisor.nekojs.fabric.event;

import com.tkisor.nekojs.bindings.event.EntityEvents;
import com.tkisor.nekojs.wrapper.event.entity.EntityLeaveLevelEventJS;
import com.tkisor.nekojs.wrapper.event.entity.EntityTickEventJS;
import com.tkisor.nekojs.wrapper.event.entity.LivingDropsEventJS;
import com.tkisor.nekojs.wrapper.event.entity.MobFinalizeSpawnEventJS;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ServerLevelAccessor;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Fabric bridge for the second group of entity events: drops, finalizeSpawn, tickPre, tickPost
 * and leaveLevel. The buses live in the node twin {@link EntityEvents}, whose "EntityEvents"
 * group merges by name with joinLevel/death/damagePre/damagePost from v1; this class only
 * supplies the dispatch entry points.
 *
 * <p>drops, finalizeSpawn and both tick events come from {@code fabric/mixin/*}: fabric-api
 * 5.0.2 entity-events-v1 only converts Elytra/Sleep/Combat/Living damage and death and has no
 * drop, spawn or tick callbacks (checked with javap). leaveLevel uses fabric-api
 * {@code ServerEntityEvents.ENTITY_UNLOAD}, whose FQN now lives in
 * {@code net.fabricmc.fabric.api.event.lifecycle.v1}. {@code NekoJSFabricMod} must not be
 * changed, so {@code MixinEntityTick} registers ENTITY_UNLOAD lazily on the first tick through
 * {@link #ensureRegistered()}, which is idempotent.
 */
public final class FabricEntityEventBindingsV2 {

    private static final AtomicBoolean UNLOAD_REGISTERED = new AtomicBoolean(false);

    private FabricEntityEventBindingsV2() {}

    /**
     * Registers the fabric-api callback for leaveLevel lazily; calling it again has no effect.
     * {@code MixinEntityTick} calls it on the first entity tick, which on the server always comes
     * before any entity unloads or leaves a level. When this code is wired into
     * {@code NekoJSFabricMod.onInitialize}, move this call there instead.
     */
    public static void ensureRegistered() {
        if (!UNLOAD_REGISTERED.compareAndSet(false, true)) {
            return;
        }
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) ->
                EntityEvents.LEAVE_LEVEL.post(new EntityLeaveLevelEventJS(entity, level), entity.getType()));
    }

    /**
     * Death drops: called by {@code MixinLivingEntityDeathDrops} at the entry of
     * {@code LivingEntity#dropAllDeathLoot(ServerLevel, DamageSource)}.
     *
     * @return {@code true} if a listener cancelled the vanilla drops (the mixin then calls
     *         {@code ci.cancel()}; items that scripts pushed into {@code event.drops} are spawned
     *         with {@code Entity#spawnAtLocation} at that point)
     */
    public static boolean postDrops(LivingEntity entity, ServerLevel level, DamageSource source) {
        if (!EntityEvents.DROPS.hasListeners()) {
            return false;
        }
        LivingDropsEventJS payload = new LivingDropsEventJS(entity, source, List.of());
        boolean cancelled = EntityEvents.DROPS.post(payload, entity.getType());
        if (cancelled) {
            for (ItemStack stack : payload.getDrops()) {
                if (!stack.isEmpty()) {
                    entity.spawnAtLocation(level, stack);
                }
            }
        }
        return cancelled;
    }

    /** Spawn setup finished: {@code MixinMobFinalizeSpawn} calls this at the TAIL of {@code Mob#finalizeSpawn}. */
    public static void postFinalizeSpawn(Mob mob, ServerLevelAccessor level, DifficultyInstance difficulty,
                                         EntitySpawnReason spawnType, SpawnGroupData spawnData) {
        if (!EntityEvents.FINALIZE_SPAWN.hasListeners()) {
            return;
        }
        EntityEvents.FINALIZE_SPAWN.post(
                new MobFinalizeSpawnEventJS(mob, level, difficulty, spawnType, spawnData),
                mob.getType());
    }

    /** Before an entity ticks: {@code MixinEntityTick} calls this at the HEAD of {@code Entity#tick}
     *  (client entities are already filtered out). */
    public static void postTickPre(Entity entity) {
        if (!EntityEvents.TICK_PRE.hasListeners()) {
            return;
        }
        EntityEvents.TICK_PRE.post(new EntityTickEventJS(entity, entity.level()), entity.getType());
    }

    /** After an entity ticks: {@code MixinEntityTick} calls this at the TAIL of {@code Entity#tick}
     *  (client entities are already filtered out). */
    public static void postTickPost(Entity entity) {
        if (!EntityEvents.TICK_POST.hasListeners()) {
            return;
        }
        EntityEvents.TICK_POST.post(new EntityTickEventJS(entity, entity.level()), entity.getType());
    }
}
