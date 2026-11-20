//? if neoforge {
package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.event.DispatchKey;
import com.tkisor.nekojs.api.event.EventBusForgeBridge;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.api.event.StableEntityEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.*;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.function.Function;

/**
 * Entity and mob event group (server scripts): damage, death, drops, spawn finalization, tick,
 * level join/leave, and item use, dispatched by {@link EntityType} or {@link Item}.
 */
@Doc("Entity and mob event group for server scripts (damage, death, drops, ticks, level lifecycle, and item use).")
public interface EntityEvents {
    /** Event group published as {@code EntityEvents} in the versioned and stable event modules. */
    EventGroup GROUP = EventGroup.of("EntityEvents");

    // damagePre: LivingDamageEvent.Pre does not implement ICancellableEvent (the same in 21.1.227,
    // 26.1.2.71 and 26.2.0.57). The default predicate path would freeze the bus as
    // non-cancellable, so a script returning true would silently do nothing. This bus is created
    // cancellable explicitly, and FORGE_BRIDGE maps a cancellation to setNewDamage(0): damage drops
    // to 0 while the native damage chain still runs, so damagePost still fires with 0 damage.
    /**
     * Fired before a living entity takes damage. Dispatched by entity type; cancelling sets
     * the new damage to 0 while still allowing {@link #DAMAGE_POST} to fire.
     */
    @Doc("Fired before a living entity takes damage (dispatched by entity type). Return true to negate damage (sets damage to 0).")
    EventBusJS<LivingDamageEvent.Pre, EntityType<?>> DAMAGE_PRE =
            GROUP.add("damagePre", ScriptType.SERVER, EventBusJS.of(
                    LivingDamageEvent.Pre.class, true, dispatchByEntity(LivingDamageEvent::getEntity)), api -> {
                api.versioned();
                api.stable()
                        .when(event -> ServerLevelEventThread.isCurrent(event.getEntity().level()))
                        .view(StableEntityEvent.class,
                                e -> NeoForgeStableEventProjections.entityDamage(e.getEntity(), e.getSource(), e.getNewDamage()))
                        .selector(String.class, NeoForgeStableEventProjections::resolveEntityTypeSelector)
                        .cancellable()
                        .scriptTypes(ScriptType.SERVER);
            });

    /** Fired after a living entity takes damage. Dispatched by entity type. */
    @Doc("Fired after a living entity takes damage (dispatched by entity type). Not cancellable.")
    EventBusJS<LivingDamageEvent.Post, EntityType<?>> DAMAGE_POST =
            GROUP.server("damagePost", LivingDamageEvent.Post.class, dispatchByEntity(LivingDamageEvent::getEntity), api -> {
                api.versioned().when(NeoForgeDamagePostRouting::versioned);
                api.stable()
                        .when(event -> ServerLevelEventThread.isCurrent(event.getEntity().level())
                                && NeoForgeDamagePostRouting.stable(event))
                        .view(StableEntityEvent.class, e -> NeoForgeStableEventProjections.entityDamage(
                                e.getEntity(), e.getSource(), NeoForgeDamagePostRouting.amount(e)))
                        .selector(String.class, NeoForgeStableEventProjections::resolveEntityTypeSelector)
                        .scriptTypes(ScriptType.SERVER);
            });

    /** Fired when a living entity is about to die. Dispatched by entity type. */
    @Doc("Fired when a living entity dies (dispatched by entity type). Return true to cancel.")
    EventBusJS<LivingDeathEvent, EntityType<?>> DEATH =
            GROUP.server("death", LivingDeathEvent.class, dispatchByEntity(LivingDeathEvent::getEntity), api -> {
                api.versioned();
                api.stable()
                        .when(event -> ServerLevelEventThread.isCurrent(event.getEntity().level()))
                        .view(StableEntityEvent.class,
                                e -> NeoForgeStableEventProjections.entityDamage(e.getEntity(), e.getSource(), 0.0f))
                        .selector(String.class, NeoForgeStableEventProjections::resolveEntityTypeSelector)
                        .cancellable()
                        .scriptTypes(ScriptType.SERVER);
            });

    /** Fired when a living entity drops loot on death. Dispatched by entity type. */
    @Doc("Fired when a living entity's death drops are generated (dispatched by entity type). Return true to cancel.")
    EventBusJS<LivingDropsEvent, EntityType<?>> DROPS =
            GROUP.server("drops", LivingDropsEvent.class, dispatchByEntity(LivingDropsEvent::getEntity), api -> {
                api.versioned();
                api.stable()
                        .when(event -> ServerLevelEventThread.isCurrent(event.getEntity().level()))
                        .view(StableEntityEvent.class,
                                e -> NeoForgeStableEventProjections.entityDamage(e.getEntity(), e.getSource(), 0.0f))
                        .selector(String.class, NeoForgeStableEventProjections::resolveEntityTypeSelector)
                        .cancellable()
                        .scriptTypes(ScriptType.SERVER);
            });

    /** Fired when a mob's spawn data is finalized. Dispatched by entity type. */
    @Doc("Fired after a mob's spawn data is finalized (dispatched by entity type). Return true to cancel.")
    EventBusJS<FinalizeSpawnEvent, EntityType<?>> FINALIZE_SPAWN =
            GROUP.server("finalizeSpawn", FinalizeSpawnEvent.class, dispatchByEntity(FinalizeSpawnEvent::getEntity), api -> {
                api.versioned();
                api.stable()
                        .view(StableEntityEvent.class, e -> NeoForgeStableEventProjections.entityEvent(e.getEntity(), 0.0f))
                        .when(event -> NeoForgeSpawnFinalizationRouting.isInitializing(event)
                                && event.getLevel().getLevel().getServer().isSameThread())
                        .selector(String.class, NeoForgeStableEventProjections::resolveEntityTypeSelector)
                        .cancellable()
                        .scriptTypes(ScriptType.SERVER);
            });

    /** Fired on the server before each entity tick. Dispatched by entity type. */
    @Doc("Fired before each entity tick on the server (dispatched by entity type). Not cancellable.")
    EventBusJS<EntityTickEvent.Pre, EntityType<?>> TICK_Pre =
            GROUP.add("tickPre", ScriptType.SERVER, EventBusJS.of(
                    EntityTickEvent.Pre.class, false, dispatchByEntityType()), api -> {
                api.versioned();
                api.stable()
                        .when(event -> ServerLevelEventThread.isCurrent(event.getEntity().level()))
                        .view(StableEntityEvent.class, e -> NeoForgeStableEventProjections.entityEvent(e.getEntity(), 0.0f))
                        .selector(String.class, NeoForgeStableEventProjections::resolveEntityTypeSelector)
                        .scriptTypes(ScriptType.SERVER);
            });

    /** Fired on the server after each entity tick. Dispatched by entity type. */
    @Doc("Fired after each entity tick on the server (dispatched by entity type). Not cancellable.")
    EventBusJS<EntityTickEvent.Post, EntityType<?>> TICK_Post =
            GROUP.server("tickPost", EntityTickEvent.Post.class, dispatchByEntityType(), api -> {
                api.versioned();
                api.stable()
                        .when(event -> ServerLevelEventThread.isCurrent(event.getEntity().level()))
                        .view(StableEntityEvent.class, e -> NeoForgeStableEventProjections.entityEvent(e.getEntity(), 0.0f))
                        .selector(String.class, NeoForgeStableEventProjections::resolveEntityTypeSelector)
                        .scriptTypes(ScriptType.SERVER);
            });

    /** Fired on the server when an entity joins a level. Dispatched by entity type. */
    @Doc("Fired when an entity joins a server level (dispatched by entity type). Return true to cancel.")
    EventBusJS<EntityJoinLevelEvent, EntityType<?>> JOIN_LEVEL =
            GROUP.server("joinLevel", EntityJoinLevelEvent.class, dispatchByEntityType(), api -> {
                api.versioned().when(event -> !event.getEntity().level().isClientSide());
                api.stable()
                        .when(event -> event.getLevel() instanceof ServerLevel level && level.getServer().isSameThread())
                        .view(StableEntityEvent.class,
                                e -> NeoForgeStableEventProjections.entityEvent(e.getEntity(), e.getLevel(), 0.0f))
                        .selector(String.class, NeoForgeStableEventProjections::resolveEntityTypeSelector)
                        .cancellable()
                        .scriptTypes(ScriptType.SERVER);
            });

    /** Fired on the server when an entity leaves a level. Dispatched by entity type. */
    @Doc("Fired when an entity leaves a server level (dispatched by entity type). Not cancellable.")
    EventBusJS<EntityLeaveLevelEvent, EntityType<?>> LEAVE_LEVEL =
            GROUP.server("leaveLevel", EntityLeaveLevelEvent.class, dispatchByEntityType(), api -> {
                api.versioned().when(event -> !event.getEntity().level().isClientSide());
                api.stable()
                        .when(event -> event.getLevel() instanceof ServerLevel level && level.getServer().isSameThread())
                        .view(StableEntityEvent.class,
                                e -> NeoForgeStableEventProjections.entityEvent(e.getEntity(), e.getLevel(), 0.0f))
                        .selector(String.class, NeoForgeStableEventProjections::resolveEntityTypeSelector)
                        .scriptTypes(ScriptType.SERVER);
            });

    /** Fired when a living entity starts using an item. Dispatched by item. */
    @Doc("Fired when a living entity starts using an item (dispatched by item id). Return true to cancel.")
    EventBusJS<LivingEntityUseItemEvent.Start, Item> USE_START =
            GROUP.server("useItemStarted", LivingEntityUseItemEvent.Start.class, dispatchByItem(LivingEntityUseItemEvent::getItem), api -> {
                api.versioned();
                api.stable()
                        .when(e -> ServerLevelEventThread.isCurrent(e.getEntity().level()))
                        .view(StableEntityEvent.class, e -> NeoForgeStableEventProjections.entityEvent(e.getEntity(), e.getDuration()))
                        .selector(String.class, NeoForgeStableEventProjections::resolveItemSelector)
                        .cancellable()
                        .scriptTypes(ScriptType.SERVER);
            });

    /** Fired when a living entity stops using an item early. Dispatched by item. */
    @Doc("Fired when a living entity stops using an item early (dispatched by item id). Return true to cancel.")
    EventBusJS<LivingEntityUseItemEvent.Stop, Item> USE_STOP =
            GROUP.server("useItemStopped", LivingEntityUseItemEvent.Stop.class, dispatchByItem(LivingEntityUseItemEvent::getItem), api -> {
                api.versioned();
                api.stable()
                        .when(e -> ServerLevelEventThread.isCurrent(e.getEntity().level())
                                && !e.getItem().isEmpty() && ItemStack.isSameItem(e.getItem(),
                                        e.getEntity().getItemInHand(e.getEntity().getUsedItemHand())))
                        .view(StableEntityEvent.class, e -> NeoForgeStableEventProjections.entityEvent(e.getEntity(), e.getDuration()))
                        .selector(String.class, NeoForgeStableEventProjections::resolveItemSelector)
                        .cancellable()
                        .scriptTypes(ScriptType.SERVER);
            });

    /** Fired when a living entity finishes using an item. Dispatched by item. */
    @Doc("Fired when a living entity finishes using an item (dispatched by item id).")
    EventBusJS<LivingEntityUseItemEvent.Finish, Item> USE_FINISHED =
            GROUP.server("useItemFinished", LivingEntityUseItemEvent.Finish.class, dispatchByItem(LivingEntityUseItemEvent::getItem), api -> {
                api.versioned();
                api.stable()
                        .when(e -> ServerLevelEventThread.isCurrent(e.getEntity().level()))
                        .view(StableEntityEvent.class, e -> NeoForgeStableEventProjections.entityEvent(e.getEntity(), e.getDuration()))
                        .selector(String.class, NeoForgeStableEventProjections::resolveItemSelector)
                        .scriptTypes(ScriptType.SERVER);
            });

    /** Fired each tick while a living entity is using an item. Dispatched by item. */
    @Doc("Fired every tick while a living entity is using an item (dispatched by item id). Return true to cancel.")
    EventBusJS<LivingEntityUseItemEvent.Tick, Item> USE_TICK =
            GROUP.server("useItemTick", LivingEntityUseItemEvent.Tick.class, dispatchByItem(LivingEntityUseItemEvent::getItem), api -> {
                api.versioned();
                api.stable()
                        .when(e -> ServerLevelEventThread.isCurrent(e.getEntity().level()))
                        .view(StableEntityEvent.class, e -> NeoForgeStableEventProjections.entityEvent(e.getEntity(), e.getDuration()))
                        .selector(String.class, NeoForgeStableEventProjections::resolveItemSelector)
                        .cancellable()
                        .scriptTypes(ScriptType.SERVER);
            });

    private static <T> DispatchKey<T, Item> dispatchByItem(Function<T, ItemStack> toStack) {
        return DispatchKey.of(Item.class, toStack.andThen(ItemStack::getItem));
    }

    private static <T extends EntityEvent> DispatchKey<T, EntityType<?>> dispatchByEntityType() {
        return dispatchByEntity(EntityEvent::getEntity);
    }

    private static <T> DispatchKey<T, EntityType<?>> dispatchByEntity(Function<T, ? extends Entity> toEntity) {
        return DispatchKey.of(EntityType.class, event -> toEntity.apply(event).getType());
    }

    /** Native NeoForge event bus bridge for entity events. */
    EventBusForgeBridge FORGE_BRIDGE = EventBusForgeBridge.create(NeoForge.EVENT_BUS)
            // Cancelling means setNewDamage(0): the native Pre event has no cancellation, so zeroing the damage is the cancel.
            .bindCancellable(DAMAGE_PRE, event -> event.setNewDamage(0))
            .bind(DAMAGE_POST, NeoForgeDamagePostRouting::capture)
            .bind(DEATH)
            .bind(DROPS)
            .bind(FINALIZE_SPAWN)
            // EntityTick, EntityJoinLevel and LeaveLevel fire on both logical sides: the SERVER bus delivers
            // only server instances (client entity tick and join run on the Render thread, and entering
            // the SERVER Context there is rejected).
            .bind(TICK_Pre, e -> !e.getEntity().level().isClientSide())
            .bind(TICK_Post, e -> !e.getEntity().level().isClientSide())
            .bind(JOIN_LEVEL, e -> !e.getEntity().level().isClientSide() || e.getLevel() instanceof ServerLevel)
            .bind(LEAVE_LEVEL, e -> !e.getEntity().level().isClientSide() || e.getLevel() instanceof ServerLevel)
            .bind(USE_START, e -> e.getEntity().level() instanceof ServerLevel)
            .bind(USE_STOP, e -> e.getEntity().level() instanceof ServerLevel)
            .bind(USE_FINISHED, e -> e.getEntity().level() instanceof ServerLevel)
            .bind(USE_TICK, e -> e.getEntity().level() instanceof ServerLevel);
}
//?}
