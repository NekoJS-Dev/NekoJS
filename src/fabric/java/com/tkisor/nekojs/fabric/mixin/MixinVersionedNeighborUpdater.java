package com.tkisor.nekojs.fabric.mixin;

import com.tkisor.nekojs.fabric.event.FabricBlockEventBindingsV2;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.redstone.NeighborUpdater;
import net.minecraft.world.level.redstone.Orientation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NeighborUpdater.class)
public interface MixinVersionedNeighborUpdater {
    @Inject(method = "executeUpdate(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/Block;Lnet/minecraft/world/level/redstone/Orientation;Z)V",
            at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private static void nekojs$onNeighborNotify(Level level, BlockState state, BlockPos pos,
                                                Block sourceBlock, Orientation orientation,
                                                boolean forceRedstone, CallbackInfo ci) {
        if (!level.isClientSide() && FabricBlockEventBindingsV2.postNeighborNotify(
                level, state, pos, sourceBlock, orientation, forceRedstone)) {
            ci.cancel();
        }
    }
}
