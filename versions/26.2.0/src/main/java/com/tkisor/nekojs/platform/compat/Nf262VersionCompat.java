package com.tkisor.nekojs.platform.compat;

import net.minecraft.world.entity.EntityTypes;

/**
 * 26.2 implementation of {@link McVersionCompat.Impl}. The {@code LIGHTNING_BOLT} constant moved to
 * {@code EntityTypes}; 26.1 had it in {@code EntityType}.
 * Registered through {@code META-INF/services/com.tkisor.nekojs.platform.compat.McVersionCompat$Impl}.
 */
public final class Nf262VersionCompat implements McVersionCompat.Impl {

    @Override
    public net.minecraft.world.entity.EntityType<?> lightningBoltType() {
        return EntityTypes.LIGHTNING_BOLT;
    }
}
