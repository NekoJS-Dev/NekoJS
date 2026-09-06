package com.tkisor.nekojs.mixin.inject;

import com.tkisor.nekojs.api.inject.ItemExtension;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Item.class)
public abstract class MixinItem implements ItemExtension {
}
