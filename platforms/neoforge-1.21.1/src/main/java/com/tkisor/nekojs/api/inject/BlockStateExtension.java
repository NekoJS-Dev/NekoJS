package com.tkisor.nekojs.api.inject;

import com.tkisor.nekojs.api.annotation.RemapByPrefix;
import net.minecraft.world.level.block.state.BlockState;

@RemapByPrefix("neko$")
public interface BlockStateExtension {
    private BlockState self() {
        return (BlockState) this;
    }

//    default boolean neko$hasTag(String tagLocation) {
//        Identifier loc = Identifier.tryParse(tagLocation);
//        if (loc == null) return false;
//
//        TagKey<Block> tagKey = TagKey.create(Registries.BLOCK, loc);
//        return self().is(tagKey);
//    }
//
//    default boolean neko$is(String idOrTag) {
//        if (idOrTag.startsWith("#")) {
//            return neko$hasTag(idOrTag.substring(1));
//        }
//        return neko$getId().equals(idOrTag);
//    }

    default String neko$getId() {
        return self().getBlock().neko$getId();
    }
}