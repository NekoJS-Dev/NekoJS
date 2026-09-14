package com.tkisor.nekojs.fabric.mixin;

import com.tkisor.nekojs.fabric.event.FabricBlockEventBindingsV2;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.PortalShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * {@code BlockEvents.portalSpawn} 的 fabric 挂点：
 * {@code PortalShape#createPortalBlocks(LevelAccessor)} 的 HEAD。
 *
 * <p>javap 实证（minecraft-merged-deobf-26.1.2.jar）：
 * <ul>
 *   <li>{@code public void createPortalBlocks(LevelAccessor)}——具体方法，方法体就是
 *       传送门方块放置循环（{@code Blocks.NETHER_PORTAL.defaultBlockState()
 *       .setValue(NetherPortalBlock.AXIS, axis)} + {@code BlockPos.betweenClosed}
 *       循环）。HEAD 取消 = 一个传送门方块都不放，语义与 NeoForge
 *       PortalSpawnEvent（ICancellableEvent）一致。</li>
 *   <li>构造器是包私有的（{@code PortalShape(Axis, int, Direction, BlockPos, int, int)}，
 *       仅包内静态工厂调用）；坐标/轴向为 private 且无 getter——用 {@link Shadow}
 *       取 {@code axis}/{@code bottomLeft} 构造载荷（mixin 侧 Shadow 不需要 AW；
 *       若共享树代码要直接读这些成员才需要 AW，见接线清单）。</li>
 *   <li>全 jar 常量池扫描：{@code createPortalBlocks} 唯二引用方是本类与
 *       {@code BaseFireBlock#onPlace}（点火路径，
 *       {@code findEmptyPortalShape(level, pos, Axis.X)} 命中后放置）——
 *       一次触发点，无重复面。</li>
 * </ul>
 *
 * <p>覆盖边界（记录入接线清单）：{@code PortalForcer#createPortal}
 * （实体跨维、无现成传送门时程序化生成框架+传送门块，直接 {@code setBlock}，
 * 不经 {@code PortalShape}）不在本挂点覆盖内。
 */
@Mixin(PortalShape.class)
public abstract class MixinPortalShape {

    @Shadow
    private Direction.Axis axis;

    @Shadow
    private BlockPos bottomLeft;

    @Inject(method = "createPortalBlocks(Lnet/minecraft/world/level/LevelAccessor;)V",
            at = @At("HEAD"), cancellable = true)
    private void nekojs$onPortalSpawn(LevelAccessor level, CallbackInfo ci) {
        if (level.isClientSide()) {
            return;
        }
        // 与原版 createPortalBlocks 内部的放置状态构造完全一致（javap 实证）
        BlockState state = Blocks.NETHER_PORTAL.defaultBlockState()
                .setValue(NetherPortalBlock.AXIS, this.axis);
        if (FabricBlockEventBindingsV2.postPortalSpawn(
                level, this.bottomLeft, state, (PortalShape) (Object) this)) {
            ci.cancel();
        }
    }
}
