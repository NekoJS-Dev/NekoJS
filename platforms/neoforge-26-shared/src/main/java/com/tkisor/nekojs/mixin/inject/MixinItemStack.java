package com.tkisor.nekojs.mixin.inject;

import com.tkisor.nekojs.api.inject.ItemStackExtension;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;

/** Injects the shared ItemStack extension through the same path used by other platforms. */
@Mixin(ItemStack.class)
public abstract class MixinItemStack implements ItemStackExtension {
}
