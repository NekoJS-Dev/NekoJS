package com.tkisor.nekojs.wrapper.event.block;

import com.tkisor.nekojs.api.annotation.Doc;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Loader-neutral payload for the player right-click block event
 * ({@code BlockEvents.rightClicked}).
 *
 * <p>NeoForge passes its native {@code PlayerInteractEvent.RightClickBlock} straight through
 * (same members: getPlayer/getLevel/getPos/getHand, with state resolved from pos); fabric
 * converts {@code UseBlockCallback} in {@code FabricBlockEventBindings}.
 *
 * <p>Cancellable: when a listener returns {@code true}, NeoForge cancels the native event and
 * fabric returns {@code SUCCESS} from the callback, which skips the remaining interaction
 * handling. Fires on the server only (the binding filters client interactions).
 */
@Doc("Fired when a player right-clicks a block (BlockEvents.rightClicked).")
@Doc("Return true to consume the interaction.")
@Getter
public class BlockRightClickEventJS {

    @Doc("The clicking player.")
    private final Player player;

    @Doc("The level the interaction happened in.")
    private final Level level;

    @Doc("Position of the clicked block.")
    private final BlockPos pos;

    @Doc("Block state of the clicked block.")
    private final BlockState state;

    @Doc("The hand used (main hand or off hand).")
    private final InteractionHand hand;

    public BlockRightClickEventJS(Player player, Level level, BlockPos pos,
                                  BlockState state, InteractionHand hand) {
        this.player = player;
        this.level = level;
        this.pos = pos;
        this.state = state;
        this.hand = hand;
    }

    @Doc("The block that was clicked.")
    public net.minecraft.world.level.block.Block getBlock() {
        return state.getBlock();
    }
}
