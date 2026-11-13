package com.tkisor.nekojs.wrapper.event.item;

import com.tkisor.nekojs.api.annotation.Doc;
import lombok.Getter;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Loader-neutral payload for the player item toss event ({@code ItemEvents.dropped}).
 *
 * <p>The members follow the getters of NeoForge native {@code ItemTossEvent}
 * (getPlayer / getItem). On fabric, {@code FabricItemEventBindingsV2} +
 * {@code MixinServerPlayerItemDrop} convert the
 * {@code ServerPlayer#drop(ItemStack, boolean, boolean)} entry; the Q-key drop and the
 * inventory-drag drop both go through it, and the {@code drop} call of death drops is
 * excluded by an {@code isDeadOrDying()} filter.
 *
 * <p><b>Known difference</b>: NeoForge's {@code ItemTossEvent} is cancellable (cancelled, the
 * item disappears from the world and no ItemEntity is created, but it is still removed from
 * the inventory). On fabric this event is notification only: the hook runs <b>before</b> the
 * ItemEntity exists, so a cancel would only swallow the item silently and could not keep it in
 * the inventory. Returning {@code true} therefore does not cancel. NeoForge's
 * {@code event.entity} is the ItemEntity at the moment of the toss; the fabric payload has no
 * such member.
 */
@Doc("Fired when a player tosses an item from their inventory (ItemEvents.dropped). Not cancellable on fabric.")
@Doc("Members: player / item (ItemStack).")
@Getter
public class ItemDroppedEventJS {

    @Doc("The player dropping the item.")
    private final Player player;

    @Doc("The item stack being dropped.")
    private final ItemStack item;

    public ItemDroppedEventJS(Player player, ItemStack item) {
        this.player = player;
        this.item = item;
    }
}
