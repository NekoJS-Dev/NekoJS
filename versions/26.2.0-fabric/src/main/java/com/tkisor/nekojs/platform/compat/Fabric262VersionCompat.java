package com.tkisor.nekojs.platform.compat;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;

/**
 * Fabric implementation of {@link McVersionCompat.Impl} for 26.2, built the same way as
 * {@code Nf262VersionCompat} on the NeoForge side. The {@code LIGHTNING_BOLT} constant moved to
 * {@link EntityTypes} in 26.2; 26.1 had it in {@code EntityType}. The raw root (src/fabric) does
 * not go through stonecutter preprocessing, so a constant that shifts between versions can only be
 * placed by a per-node override; version differences belong in the compat facade or in
 * versions/&lt;node&gt;.
 *
 * <p>Why it exists: {@code inject.MixinLevel} in the shared tree makes Level implement
 * {@code LevelExtension}, and {@code neko$spawnLightning} gets the lightning entity type through
 * this facade. Without a provider on either fabric node, that call fails inside the ServiceLoader
 * step of {@code McVersionCompat} static initialization, a crash nothing catches, while the d.ts
 * already advertises the method. This class makes the call work.
 *
 * <p>Registered through
 * {@code META-INF/services/com.tkisor.nekojs.platform.compat.McVersionCompat$Impl}, the same
 * mechanism as the NeoForge nodes. The class name deliberately differs from the 26.1 side, because
 * the drift comparison pair does not allow the same name with a different body.
 */
public final class Fabric262VersionCompat implements McVersionCompat.Impl {

    @Override
    public EntityType<?> lightningBoltType() {
        return EntityTypes.LIGHTNING_BOLT;
    }
}
