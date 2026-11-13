package com.tkisor.nekojs.wrapper.event.block;

import com.tkisor.nekojs.api.annotation.Doc;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Loader-neutral payload for the player left-click block event
 * ({@code BlockEvents.leftClicked}).
 *
 * <p>NeoForge passes its native {@code PlayerInteractEvent.LeftClickBlock} straight through
 * (same members); fabric converts {@code AttackBlockCallback} in
 * {@code FabricBlockEventBindings}. Known fabric difference: the fabric-api
 * AttackBlockCallback only fires in <b>survival mode</b> and never in creative mode, while
 * NeoForge fires in all modes.
 *
 * <p>Cancellable: when a listener returns {@code true}, NeoForge cancels the native event and
 * fabric returns {@code SUCCESS} from the callback, which skips the attack handling. Fires on
 * the server only (the binding filters client interactions).
 */
@Doc("Fired when a player left-clicks a block (BlockEvents.leftClicked).")
@Doc("Return true to consume the attack interaction.")
@Getter
public class BlockLeftClickEventJS {

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

    public BlockLeftClickEventJS(Player player, Level level, BlockPos pos,
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
