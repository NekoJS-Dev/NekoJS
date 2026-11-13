//? if >=26 {
// Orientation (net.minecraft.world.level.redstone) does not exist in 1.21.1, so the payload
// goes with the rest of the 26.x surface behind the version guard (on 1.21.1, neighborNotify
// still uses the NeoForge native event and does not go through this class).
package com.tkisor.nekojs.wrapper.event.block;

import com.tkisor.nekojs.api.annotation.Doc;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.redstone.Orientation;

/**
 * Loader-neutral payload for the block neighbor notify event
 * ({@code BlockEvents.neighborNotify}).
 *
 * <p>The member names follow NeoForge 26.x {@code BlockEvent.NeighborNotifyEvent}:
 * {@code event.level} / {@code event.pos} / {@code event.state} (the notified block) /
 * {@code event.forceRedstoneUpdate} (NeoForge's getLevel/getPos/getState/
 * getForceRedstoneUpdate; pos is the neighboring block that gets notified, state is the
 * current block state at that position). Dispatched by {@code state.getBlock()}, the same as
 * {@code dispatchByBlock} on the NeoForge side.
 *
 * <p><b>26.x difference: no notifiedSides</b>: NeoForge's {@code getNotifiedSides()}
 * (EnumSet&lt;Direction&gt;) has no counterpart in the 26.x vanilla notification flow, because
 * neighbor updates were reworked around the {@link Orientation} concept
 * ({@code NeighborUpdater#executeUpdate} posts per neighbor with a single {@code Orientation},
 * as javap shows), so a direction set is never collected in one place. This class uses
 * {@code orientation} + {@code sourceBlock} (the block that started the change) instead.
 * Scripts that need notifiedSides semantics have to aggregate on their own.
 *
 * <p>Cancellable: when a listener returns {@code true}, this neighbor notification is skipped
 * (the cancel happens at the {@code executeUpdate} entry, so the notified side no longer
 * receives {@code handleNeighborChanged}).
 *
 * <p>fabric coverage: in 26.x both SimpleNeighborUpdate (a single neighbor change) and
 * FullNeighborUpdate (the six neighbors of updateNeighborsAt) end up in
 * {@code NeighborUpdater#executeUpdate} (javap-verified). The server collector
 * {@code CollectingNeighborUpdater} and its client equivalent share that single sink; the
 * client side is filtered by {@code isClientSide} at the binding point, so the SERVER bus only
 * receives server instances.
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
