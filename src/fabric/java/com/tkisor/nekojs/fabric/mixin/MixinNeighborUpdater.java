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

/**
 * {@code BlockEvents.neighborNotify} 的 fabric 挂点：
 * {@code NeighborUpdater#executeUpdate} 的 HEAD（接口**静态**方法）。
 *
 * <p>javap 实证（minecraft-merged-deobf-26.1.2.jar）：
 * <ul>
 *   <li>26.x 把 1.21.x 的 {@code Level.neighborChanged/updateNeighborsAt} 网络全部
 *       搬进 {@code net.minecraft.world.level.redstone}：{@code Level} 上的同名方法已是
 *       空壳（updateNeighborsAt/neighborChanged/neighborShapeChanged 方法体均为
 *       {@code return}），{@code ServerLevel} 转发到
 *       {@code CollectingNeighborUpdater}（见 {@code ServerLevel.neighborChanged}
 *       方法体 {@code neighborUpdater.neighborChanged(...)}），客户端等价物是
 *       {@code InstantNeighborUpdater}。</li>
 *   <li>{@code NeighborUpdater.executeUpdate(Level, BlockState, BlockPos, Block,
 *       Orientation, boolean)} 带方法体：调用
 *       {@code state.handleNeighborChanged(level, pos, block, orientation, forceRedstone)}
 *       并被 try-catch 包成崩溃报告（"Block being updated" / "Source block type"）——
 *       即语义上 {@code pos/state} = 被通知的邻居方块，{@code block} = 发起变更的
 *       源方块（与 NeoForge NeighborNotifyEvent 的 pos/state 对齐，
 *       {@code getNotifiedSides()} 无 26.x 对应物，载荷以 orientation 替代）。</li>
 *   <li>两个队列消费者（{@code CollectingNeighborUpdater$SimpleNeighborUpdate} 单邻居
 *       ；{@code $FullNeighborUpdate} 对应 updateNeighborsAt）的
 *       {@code runNext(Level)} 都收敛到 {@code executeUpdate}（javap 实证）——
 *       跨客户端/服务端、单邻居/全邻居的唯一汇点；绑定处按 {@code isClientSide}
 *       过滤，SERVER 总线只收服务端实例。</li>
 * </ul>
 *
 * <p><b>频率说明</b>：任何方块变更通知（含红石高频电路）都会走到本挂点，
 * 注入实现先做 {@code isClientSide + hasListeners} 短路，无监听器时零事件对象。
 * 仍属高频通道：脚本监听器请保持轻量。
 */
@Mixin(NeighborUpdater.class)
public interface MixinNeighborUpdater {

    @Inject(method = "executeUpdate", at = @At("HEAD"), cancellable = true)
    private static void nekojs$onNeighborNotify(Level level, BlockState state, BlockPos pos,
                                                Block block, Orientation orientation,
                                                boolean forceRedstone, CallbackInfo ci) {
        if (level.isClientSide()) {
            return;
        }
        if (FabricBlockEventBindingsV2.postNeighborNotify(
                level, state, pos, block, orientation, forceRedstone)) {
            ci.cancel();
        }
    }
}
