package com.tkisor.nekojs.fabric.mixin;

import com.tkisor.nekojs.fabric.event.FabricBlockEventBindingsV2;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fabric hook (the first one) for the fluid-hardening event ({@code BlockEvents.fluidPlaced}): the
 * two {@code Level.setBlockAndUpdate} call sites inside
 * {@code LiquidBlock#shouldSpreadLiquid(Level, BlockPos, BlockState)} (INVOKE, javap offsets 114
 * and 154).
 *
 * <p>With one handler per ordinal, and newState computed the same way as vanilla (javap and patch
 * source), the script payload {@code newState} equals the value actually written: an obsidian or
 * cobblestone block placed when lava meets water, and basalt for soul soil plus blue ice.
 *
 * <p>Cancelling: {@code cir.setReturnValue(true)}. A return value of {@code true} from
 * {@code shouldSpreadLiquid} means "not hardened, flows normally", so a cancelled event leaves
 * the fluid unconverted and without fizz and the vanilla flowing continues. This differs from
 * NeoForge, where 26.1.2 has no patch point in this method and its FluidPlaceBlockEvent only
 * hooks three places in LavaFluid, so the event fires in fewer cases here.
 */
@Mixin(LiquidBlock.class)
public abstract class MixinLiquidBlock {

    private static final String HOST = "shouldSpreadLiquid(Lnet/minecraft/world/level/Level;"
            + "Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z";
    private static final String SET_BLOCK = "Lnet/minecraft/world/level/Level;setBlockAndUpdate"
            + "(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z";

    @Inject(method = HOST,
            at = @At(value = "INVOKE", target = SET_BLOCK, ordinal = 0),
            cancellable = true)
    private void nekojs$onFluidConvertObsidianCobble(Level level, BlockPos pos, BlockState state,
                                                     CallbackInfoReturnable<Boolean> cir) {
        if (level.isClientSide()) {
            return;
        }
        BlockState newState = level.getFluidState(pos).isSource()
                ? Blocks.OBSIDIAN.defaultBlockState()
                : Blocks.COBBLESTONE.defaultBlockState();
        if (FabricBlockEventBindingsV2.postFluidPlaced(level, pos, newState, state)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = HOST,
            at = @At(value = "INVOKE", target = SET_BLOCK, ordinal = 1),
            cancellable = true)
    private void nekojs$onFluidConvertBasalt(Level level, BlockPos pos, BlockState state,
                                             CallbackInfoReturnable<Boolean> cir) {
        if (level.isClientSide()) {
            return;
        }
        if (FabricBlockEventBindingsV2.postFluidPlaced(level, pos,
                Blocks.BASALT.defaultBlockState(), state)) {
            cir.setReturnValue(true);
        }
    }
}
