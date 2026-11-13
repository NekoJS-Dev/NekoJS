package com.tkisor.nekojs.wrapper.event.block;

import com.tkisor.nekojs.api.annotation.Doc;
import lombok.Getter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * Loader-neutral payload for the block entity tick event ({@code BlockEvents.blockEntityTick}).
 *
 * <p>The member name matches the NeoForge native payload {@code BlockEntityTickEvent} (an NF
 * Event subclass): {@code blockEntity}. The script surface is the same on both loaders, but
 * each loader owns its payload class.
 *
 * <p>Fires at the HEAD of {@code LevelChunk$BoundTickingBlockEntity#tick}, the same point on
 * both loaders, for every block entity that has a ticker (vanilla and script). Dispatched by
 * {@link BlockEntityType}. Not cancellable. High-frequency channel: no event object is built
 * when nothing is listening (the binding already filters out invalid entities).
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
