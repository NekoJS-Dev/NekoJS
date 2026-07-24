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
}
