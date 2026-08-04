package com.tkisor.nekojs.api.inject;

import com.tkisor.nekojs.api.annotation.RemapByPrefix;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

@RemapByPrefix("neko$")
public interface BlockStateExtension {
    private BlockState self() {
        return (BlockState) this;
    }

    /** 当前方块状态是否属于给定标签（如 {@code minecraft:mineable/pickaxe}）。 */
    default boolean neko$hasTag(String tagLocation) {
        ResourceLocation loc = ResourceLocation.tryParse(tagLocation);
        if (loc == null) return false;

        TagKey<Block> tagKey = TagKey.create(Registries.BLOCK, loc);
        return self().is(tagKey);
    }

    /**
     * 判断当前方块是否匹配给定 id 或标签：以 {@code #} 前缀表示标签，否则按方块 id 精确匹配。
     */
    default boolean neko$is(String idOrTag) {
        if (idOrTag.startsWith("#")) {
            return neko$hasTag(idOrTag.substring(1));
        }
        return neko$getId().equals(idOrTag);
    }

    default String neko$getId() {
        return ((BlockExtension) self().getBlock()).neko$getId();
    }
}
