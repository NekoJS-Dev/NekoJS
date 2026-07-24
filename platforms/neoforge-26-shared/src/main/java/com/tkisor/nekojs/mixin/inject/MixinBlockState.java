package com.tkisor.nekojs.mixin.inject;

import com.tkisor.nekojs.api.inject.BlockStateExtension;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(BlockState.class)
public class MixinBlockState implements BlockStateExtension {
}
