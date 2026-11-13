package com.tkisor.nekojs.api.inject;

import com.tkisor.nekojs.api.annotation.RemapByPrefix;
import com.tkisor.nekojs.api.spec.inject.BlockSpec;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;

@RemapByPrefix("neko$")
public interface BlockExtension extends BlockSpec {
    private Block self() {
        return (Block) this;
    }

    @Override
    default String neko$getId() {
        return BuiltInRegistries.BLOCK.getKey(self()).toString();
    }

    /** Returns the description key of this block. It is not exposed to scripts because NF's native
     * zero-argument getName() collides with it. */
    default String neko$getName() {
        return self().getDescriptionId();
    }
}
