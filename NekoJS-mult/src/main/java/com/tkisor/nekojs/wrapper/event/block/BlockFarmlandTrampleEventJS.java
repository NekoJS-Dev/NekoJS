package com.tkisor.nekojs.wrapper.event.block;

import com.tkisor.nekojs.api.annotation.Doc;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 耕地被踩塌事件（{@code BlockEvents.farmlandTrample}）的**加载器中立**载荷。
 *
 * <p>成员名对齐 NeoForge 26.x {@code BlockEvent.FarmlandTrampleEvent}：
 * {@code event.level} / {@code event.pos} / {@code event.state}（耕地方块）/
 * {@code event.entity} / {@code event.fallDistance}（NeoForge 的
 * getLevel/getPos/getState/getEntity/getFallDistance）。按
 * {@code state.getBlock()} 分发（与 NeoForge 侧 {@code dispatchByBlock} 一致）。
 *
 * <p>可取消：监听器返回 {@code true} 时该实体不把耕地踩塌（保持耕地状态，
 * 不调用 {@code FarmlandBlock#turnToDirt}；NeoForge FarmlandTrampleEvent 的
 * ICancellableEvent 语义）。
 *
 * <p>fabric 挂点：{@code MixinFarmlandBlock} 在
 * {@code FarmlandBlock#fallOn} 的 {@code turnToDirt} 调用点投递——只有在原版
 * 判定通过（ServerLevel、概率、LivingEntity、mobGriefing 或玩家、体积阈值）后
 * 才触发，与 NeoForge 语义一致（NeoForge 侧也只在判定通过后触发）。
 */
@Doc("Fired when an entity tramples farmland (BlockEvents.farmlandTrample).")
@Doc("Return true to prevent the farmland from turning to dirt.")
@Getter
public class BlockFarmlandTrampleEventJS {

    @Doc("The level the farmland is in.")
    private final Level level;

    @Doc("Position of the farmland block.")
    private final BlockPos pos;

    @Doc("Block state of the farmland block.")
    private final BlockState state;

    @Doc("The entity landing on the farmland.")
    private final Entity entity;

    @Doc("The fall distance of the landing entity.")
    private final float fallDistance;

    public BlockFarmlandTrampleEventJS(Level level, BlockPos pos, BlockState state,
                                       float fallDistance, Entity entity) {
        this.level = level;
        this.pos = pos;
        this.state = state;
        this.fallDistance = fallDistance;
        this.entity = entity;
    }

    @Doc("The farmland block.")
    public net.minecraft.world.level.block.Block getBlock() {
        return state.getBlock();
    }
}
