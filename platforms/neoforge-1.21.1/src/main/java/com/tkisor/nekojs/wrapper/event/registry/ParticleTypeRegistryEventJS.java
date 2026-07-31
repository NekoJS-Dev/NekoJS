package com.tkisor.nekojs.wrapper.event.registry;

import com.tkisor.nekojs.wrapper.registry.ParticleTypeBuilderJS;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * 粒子类型注册事件（{@code StartupEvents.registry('particleType')}）。
 */
public class ParticleTypeRegistryEventJS {

    private final RegisterEvent rawEvent;
    private final List<ParticleTypeBuilderJS> builders = new ArrayList<>();

    public ParticleTypeRegistryEventJS(RegisterEvent rawEvent) {
        this.rawEvent = rawEvent;
    }

    public ParticleTypeBuilderJS create(ResourceLocation id) {
        ParticleTypeBuilderJS builder = new ParticleTypeBuilderJS(id);
        builders.add(builder);
        return builder;
    }

    public void registerAll() {
        for (ParticleTypeBuilderJS builder : builders) {
            rawEvent.register(Registries.PARTICLE_TYPE, builder.getLocation(), builder::create);
        }
    }
}
