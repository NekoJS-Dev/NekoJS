package com.tkisor.nekojs.wrapper.event.block;

import com.tkisor.nekojs.api.annotation.Doc;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 方块放置事件（{@code BlockEvents.placed} / {@code BlockEvents.entityPlaced}）的
 * **加载器中立**载荷——两个总线共用本载荷，一次放置双投递（与 NeoForge 侧
 * placed/entityPlaced 同绑 {@code BlockEvent.EntityPlaceEvent} 的结构一致）。
 *
 * <p><b>26.x 触发范围事实（javap/补丁源码实证，两加载器一致）</b>：NeoForge 26.1.2
 * 补丁里 {@code EntityPlaceEvent} 的唯一 post 点是末影人放置
 * （{@code EnderMan$EndermanLeaveBlockGoal} 内 {@code EventHooks.onBlockPlace}）；
 * {@code BlockItem}/{@code FallingBlockEntity}/{@code WitherBoss} 都没有挂点。
 * 所以两平台本事件都<b>只在末影人放方块时触发</b>——玩家放置请用
 * {@code BlockEvents.rightClicked}（放置前）感知。fabric 侧由
 * {@code MixinEndermanLeaveBlockGoal} 在 {@code canPlaceBlock} HEAD 同点接入，
 * 取消语义一致（取消则末影人保留手中方块、世界不写入）。
 *
 * <p><b>分发键差异</b>：NeoForge 侧 dispatch 键取事件快照（末影人脚下方块）的
 * block；fabric 侧取<b>被放置方块</b>的 block（本类 {@link #getBlock()}）。
 * 跨平台脚本按放置物过滤时以 fabric 语义为准，NF 侧快照行为是原生事件形态。
 */
@Doc("Fired when an entity places a block (BlockEvents.placed / entityPlaced).")
@Doc("26.x fact: on both loaders this only fires for enderman placements.")
@Doc("Return true to cancel the placement (the entity keeps the carried block).")
@Getter
public class BlockPlacedEventJS {

    @Doc("The level the block is placed in.")
    private final Level level;

    @Doc("Position the block is placed at.")
    private final BlockPos pos;

    @Doc("The block state being placed.")
    private final BlockState state;

    @Doc("Block state the placement rests against (the floor).")
    private final BlockState placedAgainst;

    @Doc("The entity placing the block.")
    private final Entity entity;

    public BlockPlacedEventJS(Level level, BlockPos pos, BlockState state,
                              BlockState placedAgainst, Entity entity) {
        this.level = level;
        this.pos = pos;
        this.state = state;
        this.placedAgainst = placedAgainst;
        this.entity = entity;
    }

    @Doc("The block being placed.")
    public Block getBlock() {
        return state.getBlock();
    }
}
