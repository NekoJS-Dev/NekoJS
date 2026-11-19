//? if neoforge {
package com.tkisor.nekojs.event.level;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.Event;

/**
 * Block entity tick event ({@code BlockEvents.blockEntityTick}).
 *
 * <p>Vanilla has no matching NeoForge event, so
 * {@code LevelChunkBoundTickingBlockEntityMixin} injects at the HEAD of
 * {@code LevelChunk$BoundTickingBlockEntity.tick} and posts to {@code NeoForge.EVENT_BUS}.
 * It fires for every block entity that has a ticker (vanilla and script).
 */
public class BlockEntityTickEvent extends Event {
    private final BlockEntity blockEntity;

    public BlockEntityTickEvent(BlockEntity blockEntity) {
        this.blockEntity = blockEntity;
    }

    public BlockEntity getBlockEntity() { return blockEntity; }
    public BlockEntityType<?> getType() { return blockEntity.getType(); }

    public static final class Actual extends BlockEntityTickEvent {
        private final ServerLevel level;
        private final BlockPos pos;

        public Actual(BlockEntity blockEntity, ServerLevel level, BlockPos pos) {
            super(blockEntity);
            this.level = level;
            this.pos = pos;
        }

        public ServerLevel level() { return level; }
        public BlockPos pos() { return pos; }
    }
}
//?}
