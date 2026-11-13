package com.tkisor.nekojs.wrapper.event.item;

import com.tkisor.nekojs.api.annotation.Doc;
import lombok.Getter;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Loader-neutral payload for the item entity pickup event
 * ({@code ItemEvents.canPickUp / pickedUpPre / pickedUp}).
 *
 * <p>The member names follow the getters of NeoForge native
 * {@code ItemEntityPickupEvent} (getPlayer / getItemEntity / getItem). On fabric,
 * {@code FabricItemEventBindingsV2} + {@code MixinItemEntityPickup} convert
 * {@code ItemEntity#playerTouch} (Pre at the method entry, Post after a successful pickup).
 *
 * <p>Cancel semantics: {@code canPickUp} / {@code pickedUpPre} (an alias) are cancellable;
 * a listener {@code return true} prevents this pickup, which matches cancelling NeoForge Pre
 * and on fabric skips the whole {@code playerTouch} flow. {@code pickedUp} is the
 * after-success notification and is not cancellable.
 */
@Doc("Fired when a player is about to pick up / has picked up an item entity (ItemEvents.canPickUp / pickedUpPre / pickedUp).")
@Doc("canPickUp / pickedUpPre are cancellable: return true to prevent the pickup. pickedUp is not cancellable.")
@Getter
public class ItemEntityPickupEventJS {

    @Doc("The player touching the item.")
    private final Player player;

    @Doc("The item entity that is being / was picked up.")
    private final ItemEntity itemEntity;

    public ItemEntityPickupEventJS(Player player, ItemEntity itemEntity) {
        this.player = player;
        this.itemEntity = itemEntity;
    }

    @Doc("The item stack on the item entity.")
    public ItemStack getItem() {
        return itemEntity.getItem();
    }
}
