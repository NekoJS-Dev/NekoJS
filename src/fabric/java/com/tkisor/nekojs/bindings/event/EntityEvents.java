// Fabric copy of the shared tree's EntityEvents interface, which is guarded file-wide by
// `//? if neoforge` and passes bus calls straight to the native NeoForge event classes. On
// Fabric, joinLevel/death/damagePre/damagePost come from FabricEntityEventBindings (each group
// instance registers on its own, and groups with the same name merge). This copy provides
// drops/finalizeSpawn/tickPre/tickPost/leaveLevel with neutral payloads, delivered by
// FabricEntityEventBindingsV2 + fabric/mixin/*.
// These buses are only visible to scripts after FabricCorePlugin.registerEvents registers them.
// It registers only FabricEntityEventBindings.ENTITY_EVENTS so far, so
// registry.register(com.tkisor.nekojs.bindings.event.EntityEvents.GROUP); is still missing.
package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.event.DispatchKey;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.wrapper.event.entity.EntityLeaveLevelEventJS;
import com.tkisor.nekojs.wrapper.event.entity.EntityTickEventJS;
import com.tkisor.nekojs.wrapper.event.entity.LivingDropsEventJS;
import com.tkisor.nekojs.wrapper.event.entity.MobFinalizeSpawnEventJS;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

/** Entity and mob event group for server scripts: drops, spawn initialization, tick and leaving the
 * level (the Fabric subset). Dispatched by entity type. */
public interface EntityEvents {
    EventGroup GROUP = EventGroup.of("EntityEvents");

    /** Death drops (server scripts): dispatched by entity type; returning true skips vanilla drops
     * (scripts can read event.drops). */
    EventBusJS<LivingDropsEventJS, EntityType<?>> DROPS =
            GROUP.add("drops", ScriptType.SERVER, EventBusJS.of(
                    LivingDropsEventJS.class, true,
                    DispatchKey.of(EntityType.class, event -> event.getEntity().getType())));

    /** Spawn initialization finished (server scripts): dispatched by entity type. Notification only;
     * Fabric cannot cancel it or change the mob. */
    EventBusJS<MobFinalizeSpawnEventJS, EntityType<?>> FINALIZE_SPAWN =
            GROUP.server("finalizeSpawn", MobFinalizeSpawnEventJS.class,
                    DispatchKey.of(EntityType.class, event -> event.getMob().getType()));

    /** Before each entity tick (server scripts): dispatched by entity type. High frequency, so keep
     * listeners light. */
    EventBusJS<EntityTickEventJS, EntityType<?>> TICK_PRE =
            GROUP.server("tickPre", EntityTickEventJS.class,
                    DispatchKey.of(EntityType.class, event -> event.getEntity().getType()));

    /** After each entity tick (server scripts): dispatched by entity type. High frequency, so keep
     * listeners light. */
    EventBusJS<EntityTickEventJS, EntityType<?>> TICK_POST =
            GROUP.server("tickPost", EntityTickEventJS.class,
                    DispatchKey.of(EntityType.class, event -> event.getEntity().getType()));

    /** Entity leaves the dimension/world (server scripts): dispatched by entity type (fabric-api
     * ENTITY_UNLOAD). */
    EventBusJS<EntityLeaveLevelEventJS, EntityType<?>> LEAVE_LEVEL =
            GROUP.server("leaveLevel", EntityLeaveLevelEventJS.class,
                    DispatchKey.of(EntityType.class, event -> event.getEntity().getType()));
}
