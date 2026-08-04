package com.tkisor.nekojs.mixin.inject;

import com.tkisor.nekojs.api.inject.BlockExtension;
import net.minecraft.block.Block;
import org.spongepowered.asm.mixin.Mixin;

/**
 * 给 1.12.2 {@link Block} 注入 {@code getId()} 扩展方法。
 * <p>{@link Block} 是 MC 类，使用默认 remap（{@code remap = true}）。
 *
 * @author ZZZank
 */
@Mixin(Block.class)
public abstract class MixinBlock implements BlockExtension {
}
