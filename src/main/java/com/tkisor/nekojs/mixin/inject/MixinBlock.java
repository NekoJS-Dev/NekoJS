package com.tkisor.nekojs.mixin.inject;

import com.tkisor.nekojs.api.inject.BlockExtension;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Block.class)
public abstract class MixinBlock implements BlockExtension {
}
