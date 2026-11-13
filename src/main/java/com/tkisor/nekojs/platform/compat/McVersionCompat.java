package com.tkisor.nekojs.platform.compat;

import net.minecraft.world.entity.EntityType;

import java.util.ServiceLoader;

/**
 * Common-side facade for compile-time symbol differences between 26.x versions.
 *
 * <p>26.1 and 26.2 differ by pure compile-time renames (such as {@code EntityType.LIGHTNING_BOLT} →
 * {@code EntityTypes.LIGHTNING_BOLT}), so shared code that references either side directly fails to
 * compile on the other. Such references go through this facade instead. The implementations live in
 * the per-version modules ({@code Nf261VersionCompat} / {@code Nf262VersionCompat} on the NeoForge
 * side, {@code Fabric261VersionCompat} / {@code Fabric262VersionCompat} on the Fabric side,
 * deliberately with different class names because the 26.1↔26.2 drift comparison forbids the same
 * name with a different body) and are resolved by ServiceLoader through {@code META-INF/services}
 * on first use.
 *
 * <p>This class may only reference types that exist on both sides and are
 * <strong>loadable on the server</strong> ({@code api.inject.LevelExtension} injects Level and is
 * reachable on both sides); client-only symbols go through {@link McClientCompat}. The 1.21.1
 * LevelExtension is a separate implementation (its time and weather APIs differ in shape) and does
 * not go through this facade, so 1.21.1 provides no {@code McVersionCompat$Impl} service entry.
 */
public final class McVersionCompat {

    private static final Impl IMPL = ServiceLoader.load(Impl.class, McVersionCompat.class.getClassLoader())
            .findFirst()
            .orElseThrow(() -> new IllegalStateException(
                    "No McVersionCompat.Impl provider on classpath: expected a per-version impl "
                            + "in the 26.1/26.2 nodes, NeoForge or Fabric (META-INF/services)"));

    private McVersionCompat() {}

    public static Impl get() {
        return IMPL;
    }

    public interface Impl {
        EntityType<?> lightningBoltType();
    }
}
