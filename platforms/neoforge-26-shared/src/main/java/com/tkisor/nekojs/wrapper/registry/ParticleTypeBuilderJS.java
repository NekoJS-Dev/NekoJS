package com.tkisor.nekojs.wrapper.registry;

import lombok.Getter;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.resources.Identifier;

/**
 * 粒子类型注册器（{@code StartupEvents.registry('particleType')}）。
 *
 * <p>脚本可选 {@code overrideLimiter} 使粒子无视距离限制；其余由粒子定义
 * JSON（{@code assets/<ns>/particles/<path>.json}）提供。
 */
public class ParticleTypeBuilderJS {
    @Getter
    private final Identifier location;

    private boolean overrideLimiter = false;

    public ParticleTypeBuilderJS(Identifier location) {
        this.location = location;
    }

    /** 启用无视距离限制。 */
    public ParticleTypeBuilderJS overrideLimiter() {
        this.overrideLimiter = true;
        return this;
    }

    public SimpleParticleType create() {
        return new SimpleParticleType(overrideLimiter);
    }
}
