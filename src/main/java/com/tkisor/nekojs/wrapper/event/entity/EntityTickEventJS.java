package com.tkisor.nekojs.wrapper.event.entity;

import com.tkisor.nekojs.api.annotation.Doc;
import lombok.Getter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/**
 * Loader-neutral payload for the entity tick event ({@code EntityEvents.tickPre / tickPost}).
 *
 * <p>The members match NeoForge native {@code EntityTickEvent} (getEntity / getLevel). fabric
 * has no ready per-entity tick callback (in fabric-api 5.0.2, entity-events-v1 only converts
 * Elytra/Sleep/Combat/Living damage and death, as javap shows), so
 * {@code FabricEntityEventBindingsV2} + {@code MixinEntityTick} convert the HEAD and TAIL of
 * {@code Entity#tick} (HEAD posts before the call, TAIL after it).
 *
 * <p><b>Cost and coverage</b>: {@code Entity#tick} runs for every entity every tick, and the
 * bus short-circuits on {@code hasListeners()} (no payload is built when nothing is
 * listening). Entities that override {@code tick()} without calling {@code super.tick()} never
 * reach it (a few entity types; see the {@code MixinEntityTick} javadoc).
 *
 * <p>Not cancellable; the SERVER bus only posts server-side instances (the mixin filters the
 * client).
 */
@Doc("Fired before / after each entity's tick (EntityEvents.tickPre / tickPost). Dispatched by entity type.")
@Doc("Not cancellable. Server-side instances only. High-frequency: use carefully.")
@Getter
public class EntityTickEventJS {

    @Doc("The ticking entity.")
    private final Entity entity;

    @Doc("The level the entity is in.")
    private final Level level;

    public EntityTickEventJS(Entity entity, Level level) {
        this.entity = entity;
        this.level = level;
    }
}
