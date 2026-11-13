package com.tkisor.nekojs.wrapper.event.entity;

import lombok.Getter;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * Loader-neutral payload for the living entity death event (first used by the fabric bridge;
 * members {@code event.entity} / {@code event.source}, the same shape as NeoForge native
 * LivingDeathEvent's getEntity/getSource).
 */
public class LivingDeathEventJS {

    @Getter
    private final LivingEntity entity;

    @Getter
    private final DamageSource source;

    public LivingDeathEventJS(LivingEntity entity, DamageSource source) {
        this.entity = entity;
        this.source = source;
    }
}
