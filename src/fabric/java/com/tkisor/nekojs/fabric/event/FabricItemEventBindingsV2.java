package com.tkisor.nekojs.fabric.event;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.bindings.event.ItemEvents;
import com.tkisor.nekojs.bindings.event.ServerLevelEventThread;
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

import java.util.function.Supplier;

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

    private static final ThreadLocal<PlayerToss> PLAYER_TOSS = new ThreadLocal<>();
    private static final ThreadLocal<TossInsertion> TOSS_INSERTION = new ThreadLocal<>();

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

    public static boolean isStablePickupPost(ItemEntityPickupEventJS event) {
        return event instanceof StablePickupPost;
    }

    public static void postStablePickedUp(Player player, ItemEntity itemEntity, ItemStack pickedUp) {
        ItemEvents.PICKED_UP.postStableModuleNotification(
                new StablePickupPost(player, itemEntity, pickedUp), pickedUp.getItem(), ScriptType.SERVER);
    }

    private static final class StablePickupPost extends ItemEntityPickupEventJS {
        private StablePickupPost(Player player, ItemEntity itemEntity, ItemStack pickedUp) {
            super(player, itemEntity, pickedUp);
        }
    }

    /**
     * dropped: {@code MixinServerPlayerItemDrop} calls this at the entry of
     * {@code ServerPlayer#drop(ItemStack,boolean,boolean)}.
     *
     * @return {@code true} if a listener cancelled the drop (the mixin then returns {@code null}
     *         so no {@link ItemEntity} is spawned, matching NeoForge {@code ItemTossEvent})
     */
    public static boolean postDropped(Player player, ItemStack stack) {
        return ItemEvents.DROPPED.hasListeners() && !stack.isEmpty()
                && ItemEvents.DROPPED.post(new ItemDroppedEventJS(player, stack), stack.getItem());
    }

    private static boolean postPlayerToss(Player player, ItemEntity entity) {
        if (!ItemEvents.DROPPED.hasStableModuleListeners(ScriptType.SERVER)) return false;
        ItemStack stack = entity.getItem();
        return ItemEvents.DROPPED.postStableModuleCancellable(
                new PlayerTossEvent(player, stack), stack.getItem(), ScriptType.SERVER);
    }

    public static boolean isPlayerToss(ItemDroppedEventJS event) {
        return event instanceof PlayerTossEvent;
    }

    public static ItemEntity withPlayerToss(Player player, ItemStack stack, Supplier<ItemEntity> drop) {
        PlayerToss previous = PLAYER_TOSS.get();
        PLAYER_TOSS.set(new PlayerToss(player, stack));
        try {
            return drop.get();
        } finally {
            if (previous == null) {
                PLAYER_TOSS.remove();
            } else {
                PLAYER_TOSS.set(previous);
            }
        }
    }

    public static boolean takePlayerToss(Player player, ItemStack stack) {
        PlayerToss toss = PLAYER_TOSS.get();
        if (toss == null || toss.player() != player || toss.stack() != stack) {
            return false;
        }
        PLAYER_TOSS.remove();
        return true;
    }

    public static ItemEntity finishPlayerToss(ServerPlayer player, ItemStack stack, Supplier<ItemEntity> drop) {
        if (!ItemEvents.DROPPED.hasStableModuleListeners(ScriptType.SERVER)
                || !ServerLevelEventThread.isCurrent(player.level())) return drop.get();
        var previous = TOSS_INSERTION.get();
        var current = new TossInsertion(player, stack);
        TOSS_INSERTION.set(current);
        try {
            ItemEntity result;
            try {
                result = drop.get();
            } catch (RuntimeException | Error failure) {
                TOSS_INSERTION.remove();
                if (current.insertion != null) {
                    try {
                        current.insertion.run();
                    } catch (RuntimeException | Error insertionFailure) {
                        if (insertionFailure != failure) failure.addSuppressed(insertionFailure);
                    }
                }
                throw failure;
            }
            TOSS_INSERTION.remove();
            if (current.insertion == null) return result;
            if (result == current.entity && postPlayerToss(player, result)) return null;
            current.insertion.run();
            return result;
        } finally {
            if (previous == null) TOSS_INSERTION.remove();
            else TOSS_INSERTION.set(previous);
        }
    }

    public static boolean deferPlayerTossInsertion(LivingEntity player, ItemStack stack, Entity entity, Runnable insertion) {
        var current = TOSS_INSERTION.get();
        if (current == null || current.player != player || current.stack != stack || current.insertion != null
                || !(entity instanceof ItemEntity item)) return false;
        current.entity = item;
        current.insertion = insertion;
        return true;
    }

    private static final class TossInsertion {
        private final ServerPlayer player;
        private final ItemStack stack;
        private ItemEntity entity;
        private Runnable insertion;

        private TossInsertion(ServerPlayer player, ItemStack stack) {
            this.player = player;
            this.stack = stack;
        }
    }

    private record PlayerToss(Player player, ItemStack stack) {}

    private static final class PlayerTossEvent extends ItemDroppedEventJS {
        private PlayerTossEvent(Player player, ItemStack stack) {
            super(player, stack);
        }
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

    public static boolean isStableInteraction(PlayerEntityInteractEventJS event) {
        return event instanceof StableEntityInteraction;
    }

    public static boolean postStableEntityInteracted(ServerPlayer player, Entity target, InteractionHand hand) {
        if (!ItemEvents.ENTITY_INTERACTED.hasStableModuleListeners(ScriptType.SERVER)) return false;
        ItemStack stack = player.getItemInHand(hand);
        return ItemEvents.ENTITY_INTERACTED.postStableModuleCancellable(
                new StableEntityInteraction(player, target, stack, hand), stack.getItem(), ScriptType.SERVER);
    }

    private static final class StableEntityInteraction extends PlayerEntityInteractEventJS {
        private StableEntityInteraction(ServerPlayer player, Entity target, ItemStack stack, InteractionHand hand) {
            super(player, target, stack, hand);
        }
    }
}
