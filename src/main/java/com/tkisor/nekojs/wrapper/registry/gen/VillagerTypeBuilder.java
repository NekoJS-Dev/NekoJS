// Implementation for 26.x; this file must not contain version guards. The 1.21.1 implementation is
// the file of the same name under versions/1.21.1/src, and it must be kept in sync when behavior
// here changes.
package com.tkisor.nekojs.wrapper.registry.gen;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.npc.villager.VillagerType;

/**
 * Builder for villager types. A villager type is a pure marker with no configuration fields, and
 * its identity comes from the registration id. Textures come from
 * {@code assets/<ns>/textures/entity/villager/<type>/...}, and a data map sets the
 * biome-to-type mapping, which is not this builder's job.
 */
public class VillagerTypeBuilder extends RegistryObjectBuilder<VillagerType> {

    public VillagerTypeBuilder(Identifier id) {
        super(id);
    }

    @Override
    public VillagerType build() {
        return new VillagerType();
    }
}
