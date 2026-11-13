package com.tkisor.nekojs.wrapper.event.item;

import com.tkisor.nekojs.api.annotation.Doc;
import lombok.Getter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Loader-neutral payload for the player right-click item event
 * ({@code ItemEvents.rightClicked}).
 *
 * <p>NeoForge passes its native {@code PlayerInteractEvent.RightClickItem} straight through
 * (same members: getPlayer/getItemStack/getHand/level); fabric converts
 * {@code UseItemCallback} in {@code FabricItemEventBindings}, taking the itemStack from
 * {@code player.getItemInHand(hand)}.
 *
 * <p>Cancellable: when a listener returns {@code true}, NeoForge cancels the native event and
 * fabric returns {@code CONSUME} from the callback, which skips the remaining handling.
 */
@Doc("Fired when a player right-clicks while holding an item (ItemEvents.rightClicked).")
@Doc("Return true to consume the interaction.")
@Getter
public class ItemRightClickEventJS {

    @Doc("The clicking player.")
    private final Player player;

    @Doc("The item stack in the used hand.")
    private final ItemStack itemStack;

    @Doc("The hand used (main hand or off hand).")
    private final InteractionHand hand;

    @Doc("The level the interaction happened in.")
    private final Level level;

    public ItemRightClickEventJS(Player player, ItemStack itemStack, InteractionHand hand, Level level) {
        this.player = player;
        this.itemStack = itemStack;
        this.hand = hand;
        this.level = level;
    }
}
