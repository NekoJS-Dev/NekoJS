package com.tkisor.nekojs.wrapper.registry.gen;

import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.resources.Identifier;

/**
 * 粒子类型 builder：其余由粒子定义 JSON（{@code assets/<ns>/particles/<path>.json}）提供。
 * <pre>
 * event.particleType('mymod:spark', b =&gt; { b.overrideLimiter = true })
 * </pre>
 */
public class ParticleTypeBuilder extends RegistryObjectBuilder<SimpleParticleType> {

    /** 无视距离限制。 */
    private boolean overrideLimiter = false;

    public ParticleTypeBuilder(Identifier id) {
        super(id);
    }

    public boolean isOverrideLimiter() {
        return overrideLimiter;
    }

    public void setOverrideLimiter(boolean overrideLimiter) {
        this.overrideLimiter = overrideLimiter;
    }

    @Override
    public SimpleParticleType build() {
        return new SimpleParticleType(overrideLimiter);
    }
}
