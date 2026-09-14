package com.tkisor.nekojs.fabric.mixin;

import com.tkisor.nekojs.fabric.event.FabricBlockEventBindingsV2;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 方块放置事件（{@code BlockEvents.placed} / {@code entityPlaced}）的 fabric 挂点：
 * {@code EnderMan$EndermanLeaveBlockGoal#canPlaceBlock} 的 HEAD。
 *
 * <p>注入点选型（javap 实证 minecraft-merged-deobf-26.1.2）：原版在 {@code tick()}
 * 里随机选点后调 {@code canPlaceBlock(level, pos, carried, targetState, belowState,
 * below)}（唯一调用方），全部上下文都在声明参数里——比在 tick 的
 * {@code Level.setBlock} INVOKE 点 + LocalCapture 捕获局部变量干净得多。
 * 取消 = {@code cir.setReturnValue(false)} → {@code tick} 里 {@code &&} 短路，
 * {@code setBlock} 不执行、末影人保留手中方块——与 NeoForge 26.1.2 补丁
 * （{@code EventHooks.onBlockPlace} 返回 true 跳过放置）语义一致。
 *
 * <p>26.x 平台事实：NeoForge 侧 {@code BlockEvent.EntityPlaceEvent} 的唯一 post 点
 * 就是本方法同位次（补丁源码 {@code EventHooks.onBlockPlace}，BlockItem/
 * FallingBlockEntity/WitherBoss 均无挂点）——两平台本事件都只在末影人放置时触发，
 * 一次放置双总线投递（placed + entityPlaced）。
 */
@Mixin(targets = "net.minecraft.world.entity.monster.EnderMan$EndermanLeaveBlockGoal")
public abstract class MixinEndermanLeaveBlockGoal {

    @Shadow
    @Final
    private EnderMan enderman;

    @Inject(method = "canPlaceBlock(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)Z",
            at = @At("HEAD"), cancellable = true)
    private void nekojs$onEndermanPlace(Level level, BlockPos pos, BlockState carried,
                                        BlockState targetState, BlockState belowState,
                                        BlockPos below, CallbackInfoReturnable<Boolean> cir) {
        if (level.isClientSide()) {
            return;
        }
        if (FabricBlockEventBindingsV2.postPlaced(level, pos, carried, belowState, enderman)) {
            cir.setReturnValue(false);
        }
    }
}
