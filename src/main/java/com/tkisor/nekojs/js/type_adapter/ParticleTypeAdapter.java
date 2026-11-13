package com.tkisor.nekojs.js.type_adapter;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * Adapter for {@link ParticleType}: a pure id lookup delegated to {@link SimpleRegistryBasedAdapter}.
 * Accepts a string id (with {@code minecraft:} added when missing), {@code NekoId}, {@code Identifier},
 * or a host of the target type itself.
 */
public final class ParticleTypeAdapter extends SimpleRegistryBasedAdapter<ParticleType<?>> {
    @SuppressWarnings("unchecked")
    public ParticleTypeAdapter() {
        super(BuiltInRegistries.PARTICLE_TYPE, (Class<ParticleType<?>>) (Class<?>) ParticleType.class, "ParticleType");
    }
}
