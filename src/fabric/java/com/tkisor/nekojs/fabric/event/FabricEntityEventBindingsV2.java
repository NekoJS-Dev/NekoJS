package com.tkisor.nekojs.fabric.event;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.StableEntityEvent;
import com.tkisor.nekojs.bindings.event.EntityEvents;
import com.tkisor.nekojs.bindings.event.ServerLevelEventThread;
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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ServerLevelAccessor;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Fabric bridge for the second group of entity events: drops, finalizeSpawn, tickPre, tickPost,
 * leaveLevel, and the stable item-use hooks (useItemStarted, useItemStopped, useItemFinished,
 * useItemTick). The buses live in the node twin {@link EntityEvents}, whose "EntityEvents"
 * group merges by name with joinLevel/death/damagePre/damagePost from v1; this class only
 * supplies the dispatch entry points.
 *
 * <p>drops, finalizeSpawn, tick, and item-use events come from {@code fabric/mixin/*}: fabric-api
 * 5.0.2 entity-events-v1 only converts Elytra/Sleep/Combat/Living damage and death and has no
 * drop, spawn, tick, or item-use callbacks (checked with javap). leaveLevel uses fabric-api
 * {@code ServerEntityEvents.ENTITY_UNLOAD}, whose FQN now lives in
 * {@code net.fabricmc.fabric.api.event.lifecycle.v1}. {@code NekoJSFabricMod} must not be
 * changed, so {@code MixinEntityTick} registers ENTITY_UNLOAD lazily on the first tick through
 * {@link #ensureRegistered()}, which is idempotent.
 */
public final class FabricEntityEventBindingsV2 {

    private static final AtomicBoolean UNLOAD_REGISTERED = new AtomicBoolean(false);
    private static final ThreadLocal<PassengerTickDelivery> PASSENGER_TICK_DELIVERY = new ThreadLocal<>();

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

    public static boolean isStableLeave(EntityLeaveLevelEventJS event) {
        return event instanceof StableTrackingEnd;
    }

    public static void postTrackingEnd(Entity entity, ServerLevel level) {
        if (level.getServer().isSameThread() && EntityEvents.LEAVE_LEVEL.hasStableModuleListeners(ScriptType.SERVER)) {
            EntityEvents.LEAVE_LEVEL.postStableModuleNotification(
                    new StableTrackingEnd(entity, level), entity.getType(), ScriptType.SERVER);
        }
    }

    private static final class StableTrackingEnd extends EntityLeaveLevelEventJS {
        private StableTrackingEnd(Entity entity, ServerLevel level) {
            super(entity, level);
        }
    }

    /**
     * Death drops: called by {@code MixinLivingEntityDeathDrops} at the entry of
     * {@code LivingEntity#dropAllDeathLoot(ServerLevel, DamageSource)}.
     *
     * @return {@code true} if a listener cancelled the vanilla drops (the mixin then calls
     *         {@code ci.cancel()}; items that scripts pushed into {@code event.drops} are spawned
     *         with {@code Entity#spawnAtLocation} at that point)
     */
    public static List<ItemStack> collectDrops(LivingEntity entity, DamageSource source, List<ItemEntity> drops) {
        List<ItemStack> stacks = drops.stream().map(ItemEntity::getItem).toList();
        if (!EntityEvents.DROPS.hasListeners()) return new java.util.ArrayList<>(stacks);
        var payload = new LivingDropsEventJS(entity, source, stacks);
        return EntityEvents.DROPS.post(payload, entity.getType()) ? null : payload.getDrops();
    }

    private static final ThreadLocal<Mob> ACTIVE_FINALIZE_SPAWN = new ThreadLocal<>();

    public static SpawnGroupData wrapFinalizeSpawn(Mob mob, ServerLevelAccessor level, DifficultyInstance difficulty,
                                                   EntitySpawnReason reason, SpawnGroupData data,
                                                   Operation<SpawnGroupData> original) {
        Mob previous = ACTIVE_FINALIZE_SPAWN.get();
        ACTIVE_FINALIZE_SPAWN.set(mob);
        try {
            if (postFinalizeSpawn(mob, level, difficulty, reason, data)) {
                return null;
            }
            return original.call(mob, level, difficulty, reason, data);
        } finally {
            if (previous == null) {
                ACTIVE_FINALIZE_SPAWN.remove();
            } else {
                ACTIVE_FINALIZE_SPAWN.set(previous);
            }
        }
    }

    public static boolean postFallbackFinalizeSpawn(Mob mob, ServerLevelAccessor level, DifficultyInstance difficulty,
                                                    EntitySpawnReason reason, SpawnGroupData data) {
        return ACTIVE_FINALIZE_SPAWN.get() != mob && postFinalizeSpawn(mob, level, difficulty, reason, data);
    }

    /** Spawn setup finished: {@code MixinMobFinalizeSpawn} calls this at the TAIL of {@code Mob#finalizeSpawn}. */
    private static boolean postFinalizeSpawn(Mob mob, ServerLevelAccessor level, DifficultyInstance difficulty,
                                            EntitySpawnReason spawnType, SpawnGroupData spawnData) {
        if (!EntityEvents.FINALIZE_SPAWN.hasListeners()) {
            return false;
        }
        return EntityEvents.FINALIZE_SPAWN.post(
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

    public static void wrapVersionedPassengerTick(Entity entity, Operation<Void> original) {
        wrapPassengerTick(entity, original, false);
    }

    public static void wrapStablePassengerTick(Entity entity, Operation<Void> original) {
        if (entity.level().isClientSide()) {
            original.call(entity);
            return;
        }
        wrapPassengerTick(entity, original, true);
    }

    public static boolean includesVersionedTick(EntityTickEventJS event) {
        PassengerTickDelivery delivery = PASSENGER_TICK_DELIVERY.get();
        return delivery == null || delivery.event() != event || !delivery.stable();
    }

    public static boolean includesStableTick(EntityTickEventJS event) {
        if (!ServerLevelEventThread.isCurrent(event.getLevel())) return false;
        PassengerTickDelivery delivery = PASSENGER_TICK_DELIVERY.get();
        return delivery == null || delivery.event() != event || delivery.stable();
    }

    private static void wrapPassengerTick(Entity entity, Operation<Void> original, boolean stable) {
        postPassengerTick(EntityEvents.TICK_PRE, entity, stable);
        original.call(entity);
        postPassengerTick(EntityEvents.TICK_POST, entity, stable);
    }

    private static void postPassengerTick(EventBusJS<EntityTickEventJS, EntityType<?>> bus, Entity entity,
                                          boolean stable) {
        if (!bus.hasListeners()) {
            return;
        }
        var event = new EntityTickEventJS(entity, entity.level());
        PassengerTickDelivery previous = PASSENGER_TICK_DELIVERY.get();
        PASSENGER_TICK_DELIVERY.set(new PassengerTickDelivery(event, stable));
        try {
            if (stable) {
                bus.postStableModuleNotification(event, entity.getType(), ScriptType.SERVER);
            } else {
                bus.post(event, entity.getType());
            }
        } finally {
            if (previous == null) {
                PASSENGER_TICK_DELIVERY.remove();
            } else {
                PASSENGER_TICK_DELIVERY.set(previous);
            }
        }
    }

    private record PassengerTickDelivery(EntityTickEventJS event, boolean stable) {}

    /**
     * Item use started: {@code MixinLivingEntityUseItem} calls this at the HEAD of
     * {@code LivingEntity#startUsingItem(InteractionHand)} on the server.
     *
     * @return {@code true} if a listener cancelled starting item use
     */
    public static boolean postUseItemStarted(LivingEntity entity, ItemStack stack, int duration) {
        if (!EntityEvents.USE_START.hasListeners() || stack.isEmpty()
                || !ServerLevelEventThread.isCurrent(entity.level())) {
            return false;
        }
        StableEntityEvent payload = FabricStableEventProjections.entityEvent(entity, duration);
        return EntityEvents.USE_START.post(payload, stack.getItem());
    }

    /**
     * Item use stopped early: {@code MixinLivingEntityUseItem} calls this at the HEAD of
     * {@code LivingEntity#releaseUsingItem()} on the server.
     *
     * @return {@code true} if a listener cancelled releasing the item
     */
    public static boolean postUseItemStopped(LivingEntity entity, ItemStack stack, int remainingTicks) {
        if (!EntityEvents.USE_STOP.hasListeners() || stack.isEmpty()
                || !ServerLevelEventThread.isCurrent(entity.level())) {
            return false;
        }
        StableEntityEvent payload = FabricStableEventProjections.entityEvent(entity, remainingTicks);
        return EntityEvents.USE_STOP.post(payload, stack.getItem());
    }

    /**
     * Item use finished: {@code MixinLivingEntityUseItem} calls this at the HEAD of
     * {@code LivingEntity#completeUsingItem()} on the server.
     */
    public static void postUseItemFinished(LivingEntity entity, ItemStack stack, int remainingTicks) {
        if (!EntityEvents.USE_FINISHED.hasListeners() || stack.isEmpty()
                || !ServerLevelEventThread.isCurrent(entity.level())) {
            return;
        }
        StableEntityEvent payload = FabricStableEventProjections.entityEvent(entity, remainingTicks);
        EntityEvents.USE_FINISHED.post(payload, stack.getItem());
    }

    /**
     * Item use tick: {@code MixinLivingEntityUseItem} calls this at the HEAD of
     * {@code LivingEntity#updatingUsingItem()} on the server.
     *
     * @return {@code true} if a listener cancelled continuing item use
     */
    public static boolean postUseItemTick(LivingEntity entity, ItemStack stack, int remainingTicks) {
        if (!EntityEvents.USE_TICK.hasListeners() || stack.isEmpty()
                || !ServerLevelEventThread.isCurrent(entity.level())) {
            return false;
        }
        StableEntityEvent payload = FabricStableEventProjections.entityEvent(entity, remainingTicks);
        return EntityEvents.USE_TICK.post(payload, stack.getItem());
    }
}
