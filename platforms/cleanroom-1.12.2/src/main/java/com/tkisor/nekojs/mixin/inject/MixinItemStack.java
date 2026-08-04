package com.tkisor.nekojs.mixin.inject;

import com.tkisor.nekojs.api.inject.ItemStackExtension;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;

/**
 * 给 1.12.2 {@link ItemStack} 注入统一扩展方法（id / withCount / enchant / matches 等）。
 * <p>{@link ItemStack} 是 MC 类，使用默认 remap（{@code remap = true}）。
 *
 * @author ZZZank
 */
@Mixin(ItemStack.class)
public abstract class MixinItemStack implements ItemStackExtension {
}
