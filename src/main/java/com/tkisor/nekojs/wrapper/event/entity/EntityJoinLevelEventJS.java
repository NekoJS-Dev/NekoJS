package com.tkisor.nekojs.wrapper.event.entity;

import lombok.Getter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/**
 * Loader-neutral payload for the entity join level event (first used by the fabric bridge;
 * members {@code event.entity} / {@code event.level}, the same shape as NeoForge native
 * EntityJoinLevelEvent's getEntity/getLevel).
 */
public class EntityJoinLevelEventJS {

    @Getter
    private final Entity entity;

    @Getter
    private final Level level;

    public EntityJoinLevelEventJS(Entity entity, Level level) {
        this.entity = entity;
        this.level = level;
    }
}
