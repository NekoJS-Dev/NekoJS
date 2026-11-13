// 1.21.1 implementation, paired with the same-named 26.x file under src/ in the version tree.
// Its content equals that file evaluated at this node. Keep this file in sync when the 26.x
// side changes behavior.
package com.tkisor.nekojs.wrapper.registry.gen;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.VillagerType;

/**
 * Builds a villager type. The type carries no configuration fields; its identity comes from the
 * registration id. Textures live under
 * {@code assets/<ns>/textures/entity/villager/<type>/...}, and the biome-to-type mapping comes
 * from a data map, which this builder does not handle.
 */
public class VillagerTypeBuilder extends RegistryObjectBuilder<VillagerType> {

    public VillagerTypeBuilder(ResourceLocation id) {
        super(id);
    }

    @Override
    public VillagerType build() {
        return new VillagerType(id.getPath());
    }
}
