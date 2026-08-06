package com.tkisor.nekojs.api.inject;

import com.tkisor.nekojs.api.annotation.RemapByPrefix;
import com.tkisor.nekojs.api.spec.inject.BlockSpec;
import net.minecraft.block.Block;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

/**
 * 1.12.2 {@link Block} 统一扩展方法，注入到 MC 的 {@link Block} 类。
 *
 * <p>1.12.2 注册表用 {@link ForgeRegistries#BLOCKS}（对齐 1.21.1 的 {@code BuiltInRegistries.BLOCK}）。
 *
 * @see Block
 */
@RemapByPrefix("neko$")
public interface BlockExtension extends BlockSpec {

    private Block self() {
        return (Block) this;
    }

    /**
     * 取方块的注册 id（{@code minecraft:stone} 形式）。
     * 1.12.2 用 {@link ForgeRegistries#BLOCKS}。
     *
     * @return 方块 id；未注册返回 null
     */
    @Override
    default String neko$getId() {
        return ForgeRegistries.BLOCKS.getKey(self()).toString();
    }
}
