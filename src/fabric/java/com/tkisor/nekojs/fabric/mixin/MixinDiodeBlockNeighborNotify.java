package com.tkisor.nekojs.fabric.mixin;

import com.tkisor.nekojs.fabric.event.FabricBlockEventBindingsV2;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DiodeBlock.class)
public abstract class MixinDiodeBlockNeighborNotify {
    @Inject(method = "updateNeighborsInFront(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/redstone/ExperimentalRedstoneUtils;initialOrientation(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/Direction;Lnet/minecraft/core/Direction;)Lnet/minecraft/world/level/redstone/Orientation;"),
            cancellable = true)
    private void nekojs$onNotifyDiodeFront(Level level, BlockPos pos, BlockState state, CallbackInfo ci) {
        if (level instanceof ServerLevel serverLevel
                && FabricBlockEventBindingsV2.postNeighborNotify(serverLevel, pos, (DiodeBlock) (Object) this, null)) {
            ci.cancel();
        }
    }
}
