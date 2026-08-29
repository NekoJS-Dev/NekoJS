// TODO(loader-port): deferred to the LoaderBridge fabric port
//? if neoforge {
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
    public boolean overrideLimiter = false;

    public ParticleTypeBuilder(Identifier id) {
        super(id);
    }

    @Override
    public SimpleParticleType build() {
        return new SimpleParticleType(overrideLimiter);
    }
}
//?}
