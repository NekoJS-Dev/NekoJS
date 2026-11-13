package com.tkisor.nekojs.wrapper.event.entity;

import com.tkisor.nekojs.api.annotation.Doc;
import lombok.Getter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/**
 * Loader-neutral payload for the entity leaving a dimension / world event
 * ({@code EntityEvents.leaveLevel}).
 *
 * <p>The meaning is close to NeoForge {@code EntityLeaveLevelEvent} (the entity is removed
 * from the level). fabric uses fabric-api {@code ServerEntityEvents.ENTITY_UNLOAD}
 * (fabric-lifecycle-events-v1 4.1.1, whose FQN moved from {@code entity.event.v1} to
 * {@code event.lifecycle.v1}); it fires at the HEAD of
 * {@code ServerLevel$EntityCallbacks#onTrackingEnd} (source-verified: before the entity is
 * removed, which covers death removal, dimension teleport and chunk unload).
 *
 * <p>Not cancellable.
 */
@Doc("Fired when an entity is unloaded / removed from a ServerLevel (EntityEvents.leaveLevel). Dispatched by entity type.")
@Doc("Not cancellable. Server-side instances only.")
@Getter
public class EntityLeaveLevelEventJS {

    @Doc("The entity leaving the level.")
    private final Entity entity;

    @Doc("The level the entity is leaving.")
    private final Level level;

    public EntityLeaveLevelEventJS(Entity entity, Level level) {
        this.entity = entity;
        this.level = level;
    }
}
