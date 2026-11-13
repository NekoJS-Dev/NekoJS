package com.tkisor.nekojs.platform.compat;

import net.minecraft.world.entity.EntityType;

/**
 * {@link McVersionCompat.Impl} for fabric 26.1, mirroring the NeoForge {@code Nf261VersionCompat}:
 * the {@code LIGHTNING_BOLT} constant still lives in {@link EntityType} on 26.1, but moved to
 * {@code EntityTypes} on 26.2. The raw root (src/fabric) is not preprocessed by stonecutter, so a
 * constant that drifts between versions can only be placed per node. Version differences belong in
 * a compat facade or a versions/&lt;node&gt; override.
 *
 * <p>The shared {@code inject.MixinLevel} makes Level implement {@code LevelExtension}, and
 * {@code neko$spawnLightning} gets the lightning entity type through this facade. With no
 * provider on either fabric node, that call would fail in the {@code McVersionCompat} static
 * initializer's ServiceLoader lookup, a latent crash even though the d.ts already advertised the
 * method. This implementation makes the call usable.
 *
 * <p>Registered through
 * {@code META-INF/services/com.tkisor.nekojs.platform.compat.McVersionCompat$Impl}, the same
 * mechanism the NeoForge node uses. The class name deliberately differs from the 26.2 side: the
 * drift comparison rejects the same name with a different body.
 */
public final class Fabric261VersionCompat implements McVersionCompat.Impl {

    @Override
    public EntityType<?> lightningBoltType() {
        return EntityType.LIGHTNING_BOLT;
    }
}
