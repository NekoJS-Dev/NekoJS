package com.tkisor.nekojs.wrapper.registry.gen;

import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.resources.Identifier;

/**
 * Particle type builder. The rest of the definition comes from the particle JSON at
 * {@code assets/<ns>/particles/<path>.json}.
 * <pre>
 * event.particleType('mymod:spark', b =&gt; { b.overrideLimiter = true })
 * </pre>
 */
public class ParticleTypeBuilder extends RegistryObjectBuilder<SimpleParticleType> {

    /** Ignores the distance limit. */
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
