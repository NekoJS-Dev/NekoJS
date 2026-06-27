package com.tkisor.nekojs.mixin.inject;

import com.tkisor.nekojs.api.inject.ItemStackExtension;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;

/**
 * @author ZZZank
 */
@Mixin(ItemStack.class)
public abstract class MixinItemStack implements ItemStackExtension {
}
