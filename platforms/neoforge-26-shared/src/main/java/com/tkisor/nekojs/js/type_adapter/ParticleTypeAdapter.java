package com.tkisor.nekojs.js.type_adapter;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * {@link ParticleType} 适配器：纯 id 查询，委托 {@link SimpleRegistryBasedAdapter}。
 * 支持 string id（自动补 {@code minecraft:}）/ {@code NekoId} / {@code Identifier} / 自身类型 host。
 */
public final class ParticleTypeAdapter extends SimpleRegistryBasedAdapter<ParticleType<?>> {
    @SuppressWarnings("unchecked")
    public ParticleTypeAdapter() {
        super(BuiltInRegistries.PARTICLE_TYPE, (Class<ParticleType<?>>) (Class<?>) ParticleType.class, "ParticleType");
    }
}