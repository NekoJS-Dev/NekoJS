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

    /** 返回该方块的描述键。不进 spec——NF 原生 getName() 零参碰撞。 */
    default String neko$getName() {
        return self().getDescriptionId();
    }
}
