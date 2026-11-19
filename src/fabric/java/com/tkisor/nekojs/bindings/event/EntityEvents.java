// Fabric copy of EntityEvents (the shared tree's copy is guarded by `//? if neoforge`).
// On Fabric, joinLevel/death/damagePre/damagePost are provided by FabricEntityEventBindings,
// while this interface declares drops/finalizeSpawn/tickPre/tickPost/leaveLevel plus the
// stable-only item-use buses backed by mixins.
// EventGroupRegistry merges groups sharing the "EntityEvents" name.
package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.event.DispatchKey;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.api.event.StableEntityEvent;
import com.tkisor.nekojs.fabric.event.FabricEntityEventBindingsV2;
import com.tkisor.nekojs.fabric.event.FabricStableEventProjections;
import com.tkisor.nekojs.wrapper.event.entity.EntityLeaveLevelEventJS;
import com.tkisor.nekojs.wrapper.event.entity.EntityTickEventJS;
import com.tkisor.nekojs.wrapper.event.entity.LivingDropsEventJS;
import com.tkisor.nekojs.wrapper.event.entity.MobFinalizeSpawnEventJS;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;

/**
 * Entity and mob event group for server scripts (Fabric subset): drops, spawn initialization,
 * tick, level leave, and stable item-use events. Dispatched by entity type or item.
 */
@Doc("Entity and mob event group for server scripts: drops, spawn initialization, tick, and leaving the level.")
public interface EntityEvents {
    /** Event group published as {@code EntityEvents}. */
    EventGroup GROUP = EventGroup.of("EntityEvents");

    /** Death drops (server scripts): dispatched by entity type; returning true skips vanilla drops. */
    @Doc("Fired when a living entity drops loot on death; dispatched by entity type.")
    @Doc("Cancellable: return true to skip vanilla drops (scripts can inspect event.drops). Only visible in server_scripts.")
    EventBusJS<LivingDropsEventJS, EntityType<?>> DROPS =
            GROUP.add("drops", ScriptType.SERVER, EventBusJS.of(
                    LivingDropsEventJS.class, true,
                    DispatchKey.of(EntityType.class, event -> event.getEntity().getType())), api -> {
                api.versioned();
                api.stable()
                        .when(event -> ServerLevelEventThread.isCurrent(event.getEntity().level()))
                        .view(StableEntityEvent.class, FabricStableEventProjections::livingDrops)
                        .selector(String.class, FabricStableEventProjections::resolveEntityTypeSelector)
                        .cancellable()
                        .scriptTypes(ScriptType.SERVER);
            });

    /** Spawn initialization finished (server scripts): dispatched by entity type (notification only). */
    @Doc("Fired after a mob finishes spawn initialization; dispatched by entity type.")
    @Doc("Notification only on Fabric (cannot cancel or replace the mob). Only visible in server_scripts.")
    EventBusJS<MobFinalizeSpawnEventJS, EntityType<?>> FINALIZE_SPAWN =
            GROUP.add("finalizeSpawn", ScriptType.SERVER, EventBusJS.of(
                    MobFinalizeSpawnEventJS.class, true,
                    DispatchKey.of(EntityType.class, event -> event.getMob().getType())), api -> {
                api.versioned();
                api.stable()
                        .view(StableEntityEvent.class, FabricStableEventProjections::mobFinalizeSpawn)
                        .when(event -> event.getLevel().getLevel().getServer().isSameThread())
                        .selector(String.class, FabricStableEventProjections::resolveEntityTypeSelector)
                        .cancellable()
                        .scriptTypes(ScriptType.SERVER);
            });

    /** Before each entity tick (server scripts): dispatched by entity type. */
    @Doc("Fired before each entity tick on the server; dispatched by entity type.")
    @Doc("High frequency: keep listeners lightweight. Only visible in server_scripts.")
    EventBusJS<EntityTickEventJS, EntityType<?>> TICK_PRE =
            GROUP.server("tickPre", EntityTickEventJS.class,
                    DispatchKey.of(EntityType.class, event -> event.getEntity().getType()), api -> {
                api.versioned().when(FabricEntityEventBindingsV2::includesVersionedTick);
                api.stable()
                        .when(FabricEntityEventBindingsV2::includesStableTick)
                        .view(StableEntityEvent.class, FabricStableEventProjections::entityTick)
                        .selector(String.class, FabricStableEventProjections::resolveEntityTypeSelector)
                        .scriptTypes(ScriptType.SERVER);
            });

    /** After each entity tick (server scripts): dispatched by entity type. */
    @Doc("Fired after each entity tick on the server; dispatched by entity type.")
    @Doc("High frequency: keep listeners lightweight. Only visible in server_scripts.")
    EventBusJS<EntityTickEventJS, EntityType<?>> TICK_POST =
            GROUP.server("tickPost", EntityTickEventJS.class,
                    DispatchKey.of(EntityType.class, event -> event.getEntity().getType()), api -> {
                api.versioned().when(FabricEntityEventBindingsV2::includesVersionedTick);
                api.stable()
                        .when(FabricEntityEventBindingsV2::includesStableTick)
                        .view(StableEntityEvent.class, FabricStableEventProjections::entityTick)
                        .selector(String.class, FabricStableEventProjections::resolveEntityTypeSelector)
                        .scriptTypes(ScriptType.SERVER);
            });

    /** Entity leaves the level (server scripts): dispatched by entity type. */
    @Doc("Fired when an entity unloads or leaves a level; dispatched by entity type.")
    @Doc("Only visible in server_scripts.")
    EventBusJS<EntityLeaveLevelEventJS, EntityType<?>> LEAVE_LEVEL =
            GROUP.server("leaveLevel", EntityLeaveLevelEventJS.class,
                    DispatchKey.of(EntityType.class, event -> event.getEntity().getType()), api -> {
                api.versioned().when(event -> !FabricEntityEventBindingsV2.isStableLeave(event));
                api.stable()
                        .when(FabricEntityEventBindingsV2::isStableLeave)
                        .view(StableEntityEvent.class, FabricStableEventProjections::entityLeave)
                        .selector(String.class, FabricStableEventProjections::resolveEntityTypeSelector)
                        .scriptTypes(ScriptType.SERVER);
            });

    /** Fired when a living entity starts using an item (stable surface only on Fabric). */
    @Doc("Fired when a living entity starts using an item (dispatched by item id). Return true to cancel.")
    EventBusJS<StableEntityEvent, Item> USE_START =
            GROUP.add("useItemStarted", ScriptType.SERVER, EventBusJS.of(
                    StableEntityEvent.class, true,
                    DispatchKey.of(Item.class, e -> null)),
                    api -> api.stable()
                            .selector(String.class, FabricStableEventProjections::resolveItemSelector)
                            .scriptTypes(ScriptType.SERVER));

    /** Fired when a living entity stops using an item early (stable surface only on Fabric). */
    @Doc("Fired when a living entity stops using an item early (dispatched by item id). Return true to cancel.")
    EventBusJS<StableEntityEvent, Item> USE_STOP =
            GROUP.add("useItemStopped", ScriptType.SERVER, EventBusJS.of(
                    StableEntityEvent.class, true,
                    DispatchKey.of(Item.class, e -> null)),
                    api -> api.stable()
                            .selector(String.class, FabricStableEventProjections::resolveItemSelector)
                            .scriptTypes(ScriptType.SERVER));

    /** Fired when a living entity finishes using an item (stable surface only on Fabric). */
    @Doc("Fired when a living entity finishes using an item (dispatched by item id).")
    EventBusJS<StableEntityEvent, Item> USE_FINISHED =
            GROUP.add("useItemFinished", ScriptType.SERVER, EventBusJS.of(
                    StableEntityEvent.class, false,
                    DispatchKey.of(Item.class, e -> null)),
                    api -> api.stable()
                            .selector(String.class, FabricStableEventProjections::resolveItemSelector)
                            .scriptTypes(ScriptType.SERVER));

    /** Fired each tick while a living entity is using an item (stable surface only on Fabric). */
    @Doc("Fired every tick while a living entity is using an item (dispatched by item id). Return true to cancel.")
    EventBusJS<StableEntityEvent, Item> USE_TICK =
            GROUP.add("useItemTick", ScriptType.SERVER, EventBusJS.of(
                    StableEntityEvent.class, true,
                    DispatchKey.of(Item.class, e -> null)),
                    api -> api.stable()
                            .selector(String.class, FabricStableEventProjections::resolveItemSelector)
                            .scriptTypes(ScriptType.SERVER));
}
