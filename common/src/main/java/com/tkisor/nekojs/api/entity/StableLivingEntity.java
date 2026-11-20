package com.tkisor.nekojs.api.entity;

import com.tkisor.nekojs.api.annotation.Doc;

/** Live entity with health. */
@Doc("Live entity with health.")
public interface StableLivingEntity extends StableEntity {
    @Doc("Returns the entity's current health.")
    float getHealth();

    @Doc("Returns the entity's current maximum health.")
    float getMaxHealth();
}
