package com.tkisor.nekojs.fabric.event;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.event.DispatchKey;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.wrapper.entity.GoalRegistry;
import com.tkisor.nekojs.wrapper.event.entity.EntityJoinLevelEventJS;
import com.tkisor.nekojs.wrapper.event.entity.LivingDamageEventJS;
import com.tkisor.nekojs.wrapper.event.entity.LivingDeathEventJS;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

/**
 * Fabric bridge for entity events:
 * joinLevel (dispatched by entity type through our own mixin hook, because fabric-api has no
 * event for an entity joining the world, including summon and spawn; ENTITY_LOAD only covers
 * loading from storage)
 * + death (dispatched through ServerLivingEntityEvents.ALLOW_DEATH, which fires before the death
 * decision, matching NeoForge LivingDeathEvent)
 * + damagePre/damagePost (dispatched through ALLOW_DAMAGE/AFTER_DAMAGE).
 *
 * <p>Bus names and dispatch behavior match NeoForge (a script writes
 * {@code EntityEvents.joinLevel('minecraft:zombie', ...)} the same way on both loaders). The
 * payload is a neutral class and its members are named after the vanilla event getters
 * (entity/level/source).
 *
 * <p>damagePre builds a cancellable bus explicitly with
 * {@code EventBusJS.of(type, true, dispatch)}: fabric has no {@code ICancellableEvent} check
 * chain like NeoForge, and the default cancellability predicate of {@code EventGroup.server} is
 * always non-cancellable on fabric. A listener returning {@code true} makes ALLOW_DAMAGE return
 * false, which removes the damage. Known capability gap: fabric's ALLOW_DAMAGE cannot change the
 * damage amount (NeoForge Pre can); it only supports cancellation.
 */
public final class FabricEntityEventBindings {

    /** Same names as bindings/event/EntityEvents on NeoForge (a fabric subset). */
    public static final EventGroup ENTITY_EVENTS = EventGroup.of("EntityEvents");

    public static final EventBusJS<EntityJoinLevelEventJS, EntityType<?>> JOIN_LEVEL =
            ENTITY_EVENTS.server("joinLevel", EntityJoinLevelEventJS.class,
                    DispatchKey.of(EntityType.class, event -> event.getEntity().getType()));

    public static final EventBusJS<LivingDeathEventJS, EntityType<?>> DEATH =
            ENTITY_EVENTS.server("death", LivingDeathEventJS.class,
                    DispatchKey.of(EntityType.class, event -> event.getEntity().getType()));

    private static final EventBusJS<LivingDamageEventJS, EntityType<?>> DAMAGE_PRE =
            ENTITY_EVENTS.add("damagePre", ScriptType.SERVER, EventBusJS.of(
                    LivingDamageEventJS.class, true,
                    DispatchKey.of(EntityType.class, event -> event.getEntity().getType())));

    private static final EventBusJS<LivingDamageEventJS, EntityType<?>> DAMAGE_POST =
            ENTITY_EVENTS.server("damagePost", LivingDamageEventJS.class,
                    DispatchKey.of(EntityType.class, event -> event.getEntity().getType()));

    private FabricEntityEventBindings() {}

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("NekoJS-Fabric");

    public static void register() {
        LOGGER.info("FabricEntityEventBindings registering");
        // joinLevel is triggered by ServerLevelMixin through postJoinLevel after addEntity succeeds.
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, damageAmount) -> {
            DEATH.post(new LivingDeathEventJS(entity, source), entity.getType());
            return true;
        });
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
                !DAMAGE_PRE.post(new LivingDamageEventJS(entity, source, amount), entity.getType()));
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, amount, dealt, survived) ->
                DAMAGE_POST.post(new LivingDamageEventJS(entity, source, amount), entity.getType()));
    }

    /** Hook entry for {@code ServerLevelMixin}: called when a {@code ServerLevel} accepts an entity
     *  join. This runs on the server only, so it meets the SERVER bus rule by construction. */
    public static void postJoinLevel(Entity entity, ServerLevel level) {
        JOIN_LEVEL.post(new EntityJoinLevelEventJS(entity, level), entity.getType());
        // Does the same job as the EntityJoinLevelEvent listener in NekoJSMod on NeoForge: apply the
        // goals that scripts registered.
        GoalRegistry.onEntityJoinLevel(entity, level);
    }
}
