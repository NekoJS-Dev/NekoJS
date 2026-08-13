package com.tkisor.nekojs.event.level;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.Event;

/**
 * 方块实体 tick 事件（{@code BlockEvents.blockEntityTick}）。
 *
 * <p>原版没有对应的 NeoForge 事件；由 {@code LevelChunkBoundTickingBlockEntityMixin}
 * 在 {@code LevelChunk$BoundTickingBlockEntity.tick} HEAD 注入并 post 到
 * {@code NeoForge.EVENT_BUS}。对所有有 ticker 的方块实体触发（原版 + 脚本）。
 */
public class BlockEntityTickEvent extends Event {
    private final BlockEntity blockEntity;

    public BlockEntityTickEvent(BlockEntity blockEntity) {
        this.blockEntity = blockEntity;
    }

    public BlockEntity getBlockEntity() { return blockEntity; }
    public BlockEntityType<?> getType() { return blockEntity.getType(); }
}
