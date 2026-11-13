package com.tkisor.nekojs.api.inject;

import com.tkisor.nekojs.api.annotation.RemapByPrefix;
import com.tkisor.nekojs.api.spec.inject.BlockStateSpec;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

@RemapByPrefix("neko$")
public interface BlockStateExtension extends BlockStateSpec {
    private BlockState self() {
        return (BlockState) this;
    }

    /** Whether this block state belongs to the given tag (for example {@code minecraft:mineable/pickaxe}). */
    default boolean neko$hasTag(String tagLocation) {
        Identifier loc = Identifier.tryParse(tagLocation);
        if (loc == null) return false;

        TagKey<Block> tagKey = TagKey.create(Registries.BLOCK, loc);
        // 26.x removed the single-argument BlockState#is(TagKey) overload, so the holder check is used
        //? if >=26 {
        return self().typeHolder().is(tagKey);
//?} else {
        /*return self().is(tagKey);
*///?}
    }

    /**
     * Tests whether the current block matches the given id or tag: a {@code #} prefix means a tag,
     * otherwise the block id is matched exactly.
     */
    default boolean neko$is(String idOrTag) {
        if (idOrTag.startsWith("#")) {
            return neko$hasTag(idOrTag.substring(1));
        }
        return neko$getId().equals(idOrTag);
    }

    default String neko$getId() {
        // On 26.x the Block gets neko$getId injected through the interface; 1.21.1 needs a cast, and the
        // cast form is equivalent on both.
        return ((BlockExtension) self().getBlock()).neko$getId();
    }
}
