package com.tkisor.nekojs.platform.compat;

import net.minecraft.world.entity.EntityType;

/**
 * 26.1 implementation of {@link McVersionCompat.Impl}. The {@code LIGHTNING_BOLT} constant still
 * lives in {@link EntityType}; 26.2 moved it to {@code EntityTypes}.
 * Registered through {@code META-INF/services/com.tkisor.nekojs.platform.compat.McVersionCompat$Impl}.
 */
public final class Nf261VersionCompat implements McVersionCompat.Impl {

    @Override
    public EntityType<?> lightningBoltType() {
        return EntityType.LIGHTNING_BOLT;
    }
}
