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
 * 流体硬化事件（{@code BlockEvents.fluidPlaced}）的 fabric 挂点（一）：
 * {@code LiquidBlock#shouldSpreadLiquid(Level, BlockPos, BlockState)} 内两处
 * {@code Level.setBlockAndUpdate} 调用点（INVOKE，javap 实证偏移 114/154）。
 *
 * <p>原版方法体（javap + 补丁源码实证）：岩浆方块遇水邻居 →
 * 第一处（偏移 114）{@code isSource ? OBSIDIAN : COBBLESTONE}；灵魂土+蓝冰 →
 * 第二处（偏移 154）{@code BASALT}。两个 handler 按 ordinal 各挂一处，
 * newState 与原版计算式一致（脚本载荷 {@code newState} 与实际写入值相同）。
 *
 * <p>取消语义：{@code cir.setReturnValue(true)}——{@code shouldSpreadLiquid} 的
 * 返回值 {@code true} 表示"未硬化、正常流动"，即取消后流体不转化也不 fizz，
 * 继续原版流动逻辑。与 NeoForge 侧差异：NF 26.1.2 无本方法补丁点（其
 * FluidPlaceBlockEvent 只挂 LavaFluid 三处），覆盖范围语义差异记入移植台账。
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
