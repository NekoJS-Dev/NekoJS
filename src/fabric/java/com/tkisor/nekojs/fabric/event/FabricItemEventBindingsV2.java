package com.tkisor.nekojs.fabric.event;

import com.tkisor.nekojs.bindings.event.ItemEvents;
import com.tkisor.nekojs.wrapper.event.item.ItemDroppedEventJS;
import com.tkisor.nekojs.wrapper.event.item.ItemEntityPickupEventJS;
import com.tkisor.nekojs.wrapper.event.item.ItemUseFinishedEventJS;
import com.tkisor.nekojs.wrapper.event.item.PlayerEntityInteractEventJS;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Fabric bridge v2 for item events: canPickUp, pickedUpPre, pickedUp, dropped, foodEaten and
 * entityInteracted. The buses live in the node twin {@link ItemEvents}, whose "ItemEvents" group
 * merges by name with rightClicked from v1 and tooltip from the client side; this class only
 * supplies the dispatch entry points that the mixins call.
 *
 * <p>All of them hang off mixins because fabric-api has no ready-made callback (see the javap
 * notes on each mixin):
 * <ul>
 *   <li>{@code MixinItemEntityPickup} (ItemEntity#playerTouch) -> pickup pre/post</li>
 *   <li>{@code MixinServerPlayerItemDrop} (ServerPlayer#drop(ItemStack,boolean,boolean)) -> dropped</li>
 *   <li>{@code MixinLivingEntityUseItemFinish} (the INVOKE point of completeUsingItem) -> foodEaten</li>
 *   <li>{@code MixinServerGamePacketListenerItemInteract} (the INVOKE point of handleInteract)
 *       -> entityInteracted</li>
 * </ul>
 */
public final class FabricItemEventBindingsV2 {

    private FabricItemEventBindingsV2() {}

    /**
     * Pickup pre: {@code MixinItemEntityPickup} calls this at the entry of {@code playerTouch}.
     *
     * @return {@code true} if a listener cancelled the pickup (the mixin then calls
     *         {@code ci.cancel()} to skip the whole pickup)
     */
    public static boolean postCanPickUp(Player player, ItemEntity itemEntity) {
        boolean anyListeners = ItemEvents.CAN_PICK_UP.hasListeners()
                || ItemEvents.PICKED_UP_PRE.hasListeners();
        if (!anyListeners) {
            return false;
        }
        ItemEntityPickupEventJS payload = new ItemEntityPickupEventJS(player, itemEntity);
        Item item = payload.getItem().getItem();
        boolean cancelled = ItemEvents.CAN_PICK_UP.post(payload, item);
        if (!cancelled) {
            cancelled = ItemEvents.PICKED_UP_PRE.post(payload, item);
        }
        return cancelled;
    }

    /** Pickup post: {@code MixinItemEntityPickup} fires this at the {@code onItemPickup} call site in
     *  {@code playerTouch}. */
    public static void postPickedUp(Player player, ItemEntity itemEntity) {
        if (!ItemEvents.PICKED_UP.hasListeners()) {
            return;
        }
        ItemEntityPickupEventJS payload = new ItemEntityPickupEventJS(player, itemEntity);
        ItemEvents.PICKED_UP.post(payload, payload.getItem().getItem());
    }

    /** dropped: {@code MixinServerPlayerItemDrop} calls this at the entry of
     *  {@code ServerPlayer#drop(ItemStack,boolean,boolean)}. */
    public static void postDropped(Player player, ItemStack stack) {
        if (!ItemEvents.DROPPED.hasListeners() || stack.isEmpty()) {
            return;
        }
        ItemEvents.DROPPED.post(new ItemDroppedEventJS(player, stack), stack.getItem());
    }

    /** foodEaten: {@code MixinLivingEntityUseItemFinish} calls this when an item finishes being used. */
    public static void postFoodEaten(LivingEntity entity, ItemStack stack, InteractionHand hand) {
        if (!ItemEvents.FOOD_EATEN.hasListeners() || stack.isEmpty()) {
            return;
        }
        ItemEvents.FOOD_EATEN.post(new ItemUseFinishedEventJS(entity, stack, hand), stack.getItem());
    }

    /**
     * entityInteracted: {@code MixinServerGamePacketListenerItemInteract} fires this at the
     * {@code interactOn} call site in {@code handleInteract}.
     *
     * @return {@code true} if a listener cancelled the interaction (the mixin then calls
     *         {@code ci.cancel()} to skip the vanilla interaction)
     */
    public static boolean postEntityInteracted(ServerPlayer player, Entity target, ItemStack stack, InteractionHand hand) {
        if (!ItemEvents.ENTITY_INTERACTED.hasListeners()) {
            return false;
        }
        return ItemEvents.ENTITY_INTERACTED.post(
                new PlayerEntityInteractEventJS(player, target, stack, hand),
                stack.getItem());
    }
}
