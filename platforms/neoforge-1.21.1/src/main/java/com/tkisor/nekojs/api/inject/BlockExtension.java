package com.tkisor.nekojs.api.inject;

import com.tkisor.nekojs.api.annotation.RemapByPrefix;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;

@RemapByPrefix("neko$")
public interface BlockExtension {
    private Block self() {
        return (Block) this;
    }

    default String neko$getId() {
        return BuiltInRegistries.BLOCK.getKey(self()).toString();
    }

    /** 返回该方块的描述键（未本地化名称，如 {@code block.minecraft.stone}）。 */
    default String neko$getName() {
        return self().getDescriptionId();
    }
}
