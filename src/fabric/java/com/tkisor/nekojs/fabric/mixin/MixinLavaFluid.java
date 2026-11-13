package com.tkisor.nekojs.fabric.mixin;

import com.tkisor.nekojs.fabric.event.FabricBlockEventBindingsV2;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.LavaFluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fabric hook (the second one) for the fluid-hardening event ({@code BlockEvents.fluidPlaced}):
 * the {@code LevelAccessor.setBlock} call site inside {@code LavaFluid#spreadTo} (INVOKE,
 * javap offset 57; the CP owner is LevelAccessor and the single-L form matches).
 *
 * <p>Vanilla behaviour: lava flowing down into a water block hardens into {@code STONE} with a
 * fizz. This is the same place as the NeoForge 26.1.2 {@code FluidPlaceBlockEvent} post point
 * (patch source {@code EventHooks.fireFluidPlaceBlockEvent(level, pos, pos, STONE)}).
 *
 * <p>Cancelling differs slightly from NeoForge: there, cancelling swaps the setBlock argument for
 * the previous state and the fizz still plays; on fabric {@code ci.cancel()} returns from
 * {@code spreadTo} early, so nothing hardens, nothing fizzes and {@code super.spreadTo} does not
 * run. The net effect is the same: the fluid does not harden.
 */
@Mixin(LavaFluid.class)
public abstract class MixinLavaFluid {

    @Inject(method = "spreadTo(Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;"
            + "Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/Direction;"
            + "Lnet/minecraft/world/level/material/FluidState;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/LevelAccessor;setBlock"
                            + "(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"),
            cancellable = true)
    private void nekojs$onLavaSpreadToStone(LevelAccessor level, BlockPos pos, BlockState state,
                                            Direction direction, FluidState target, CallbackInfo ci) {
        if (level.isClientSide()) {
            return;
        }
        if (FabricBlockEventBindingsV2.postFluidPlaced(level, pos,
                Blocks.STONE.defaultBlockState(), state)) {
            ci.cancel();
        }
    }
}
