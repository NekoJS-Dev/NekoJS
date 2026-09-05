package com.tkisor.nekojs.wrapper.event.block;

import com.tkisor.nekojs.api.annotation.Doc;
import lombok.Getter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * 方块实体 tick 事件（{@code BlockEvents.blockEntityTick}）的**加载器中立**载荷。
 *
 * <p>成员名与 NeoForge 侧原生载荷 {@code BlockEntityTickEvent}（NF Event 子类）
 * 同形（blockEntity）——两加载器脚本面一致，载荷类各自持有（孪生决策见
 * 移植台账第 14 批）。
 *
 * <p>触发点：{@code LevelChunk$BoundTickingBlockEntity#tick} HEAD（两加载器同点），
 * 对所有有 ticker 的方块实体触发（原版 + 脚本），按 {@link BlockEntityType} 分发。
 * 不可取消。高频通道：无监听器时零事件对象（绑定处已过滤失效实体）。
 */
@Doc("Fired every tick for each ticking block entity (furnaces, machines, ...).")
@Getter
public class BlockEntityTickEventJS {

    @Doc("The ticking block entity.")
    private final BlockEntity blockEntity;

    public BlockEntityTickEventJS(BlockEntity blockEntity) {
        this.blockEntity = blockEntity;
    }

    @Doc("The block entity's type (dispatch key).")
    public BlockEntityType<?> getType() {
        return blockEntity.getType();
    }
}
