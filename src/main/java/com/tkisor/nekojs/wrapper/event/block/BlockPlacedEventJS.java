package com.tkisor.nekojs.wrapper.event.block;

import com.tkisor.nekojs.api.annotation.Doc;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Loader-neutral payload for block placement ({@code BlockEvents.placed} /
 * {@code BlockEvents.entityPlaced}). Both buses share this payload, so one placement posts to
 * both, matching the NeoForge structure where placed and entityPlaced are both bound to
 * {@code BlockEvent.EntityPlaceEvent}.
 *
 * <p><b>26.x trigger scope (verified with javap and the patch sources, the same on both
 * loaders)</b>: in the NeoForge 26.1.2 patch the only post point of {@code EntityPlaceEvent}
 * is enderman placement ({@code EventHooks.onBlockPlace} inside
 * {@code EnderMan$EndermanLeaveBlockGoal}); {@code BlockItem}, {@code FallingBlockEntity} and
 * {@code WitherBoss} have no hook. So on both platforms this event only fires when an enderman
 * places a block; to observe player placement, use {@code BlockEvents.rightClicked} (before
 * the placement). On fabric, {@code MixinEndermanLeaveBlockGoal} hooks the same point at the
 * {@code canPlaceBlock} HEAD, and cancellation behaves the same: cancelled, the enderman keeps
 * the block in its hand and nothing is written to the world.
 *
 * <p><b>Dispatch key difference</b>: NeoForge dispatches on the block from the event snapshot
 * (the block under the enderman's feet); fabric dispatches on the block being placed
 * ({@link #getBlock()} of this class). A cross-platform script that filters by the placed
 * block should follow the fabric semantics; the NeoForge snapshot is the shape of the native
 * event.
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
