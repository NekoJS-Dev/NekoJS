//? if >=26 {
// Orientation（net.minecraft.world.level.redstone）在 1.21.1 不存在——载荷随 26.x 面
// 一起走版本守卫（1.21.1 侧 neighborNotify 仍走 NeoForge 原生事件，不经本类）。
package com.tkisor.nekojs.wrapper.event.block;

import com.tkisor.nekojs.api.annotation.Doc;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.redstone.Orientation;

/**
 * 方块邻居通知事件（{@code BlockEvents.neighborNotify}）的**加载器中立**载荷。
 *
 * <p>成员名对齐 NeoForge 26.x {@code BlockEvent.NeighborNotifyEvent}：
 * {@code event.level} / {@code event.pos} / {@code event.state}（被通知的方块）/
 * {@code event.forceRedstoneUpdate}（NeoForge 的 getLevel/getPos/getState/
 * getForceRedstoneUpdate；pos = 被通知的相邻方块，state = 该位置当前方块状态）。
 * 按 {@code state.getBlock()} 分发（与 NeoForge 侧 {@code dispatchByBlock} 一致）。
 *
 * <p>**26.x 差异——无 notifiedSides**：NeoForge 的 {@code getNotifiedSides()}
 * （EnumSet&lt;Direction&gt;）在 26.x 原版通知流中不存在——邻居更新已改革为
 * {@link Orientation} 概念（{@code NeighborUpdater#executeUpdate} 按单个
 * {@code Orientation} 逐邻居投递，javap 实证），方向集合无聚合点。本例以
 * {@code orientation} + {@code sourceBlock}（发起变更的源方块）替代；
 * 若需 notifiedSides 语义，需自研聚合（见接线清单）。
 *
 * <p>可取消：监听器返回 {@code true} 时跳过本次邻居通知（{@code executeUpdate}
 * 入口取消，被通知方不再收到 {@code handleNeighborChanged}）。
 *
 * <p>fabric 覆盖说明：26.x 的 SimpleNeighborUpdate（单邻居变更）与
 * FullNeighborUpdate（updateNeighborsAt 六邻居）都收敛到
 * {@code NeighborUpdater#executeUpdate}（javap 实证）——服务端收集器
 * {@code CollectingNeighborUpdater} 与其客户端等价物共用此唯一汇点，
 * 客户端侧在绑定点按 {@code isClientSide} 过滤，SERVER 总线只收服务端实例。
 */
@Doc("Fired when a block is notified of a neighbor change (BlockEvents.neighborNotify).")
@Doc("Return true to cancel the neighbor notification.")
@Getter
public class BlockNeighborNotifyEventJS {

    @Doc("The level the update happened in.")
    private final Level level;

    @Doc("Position of the notified block (its neighbor changed).")
    private final BlockPos pos;

    @Doc("Current block state at the notified position.")
    private final BlockState state;

    @Doc("The source block that changed (26.x-level info; NeoForge getNotifiedSides has no 26.x equivalent).")
    private final Block sourceBlock;

    @Doc("Orientation of the notification (26.x redstone Orientation; replaces the direction set).")
    private final Orientation orientation;

    @Doc("Whether the redstone update is forced (skips the vanilla neighbor-linking filter).")
    private final boolean forceRedstoneUpdate;

    public BlockNeighborNotifyEventJS(Level level, BlockPos pos, BlockState state, Block sourceBlock,
                                      Orientation orientation, boolean forceRedstoneUpdate) {
        this.level = level;
        this.pos = pos;
        this.state = state;
        this.sourceBlock = sourceBlock;
        this.orientation = orientation;
        this.forceRedstoneUpdate = forceRedstoneUpdate;
    }

    @Doc("The notified block.")
    public Block getBlock() {
        return state.getBlock();
    }
}
//?}
