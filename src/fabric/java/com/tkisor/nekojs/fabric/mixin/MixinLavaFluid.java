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
 * 流体硬化事件（{@code BlockEvents.fluidPlaced}）的 fabric 挂点（二）：
 * {@code LavaFluid#spreadTo} 内 {@code LevelAccessor.setBlock} 调用点
 * （INVOKE，javap 实证偏移 57；CP owner = LevelAccessor，单 L 形式匹配）。
 *
 * <p>原版语义：岩浆向下流进水方块 → 硬化为 {@code STONE} + fizz。这与
 * NeoForge 26.1.2 的 {@code FluidPlaceBlockEvent} post 点同位次（补丁源码
 * {@code EventHooks.fireFluidPlaceBlockEvent(level, pos, pos, STONE)}）。
 *
 * <p>取消语义与 NF 的细微差异：NF 取消 = setBlock 参数换成旧状态（fizz 照放）；
 * fabric 侧 {@code ci.cancel()} = 整个 {@code spreadTo} 提前返回（本次不硬化也
 * 不 fizz、不落 super.spreadTo）。净效果一致：流体不硬化。已记入移植台账。
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
