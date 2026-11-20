//? if neoforge {
package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.event.DispatchKey;
import com.tkisor.nekojs.api.event.EventBusForgeBridge;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.api.event.StableItemEvent;
import com.tkisor.nekojs.client.ClientEventThread;
import com.tkisor.nekojs.stage.NeoForgeStageGateSubscriber;
import com.tkisor.nekojs.wrapper.event.server.ItemModificationEventJS;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.function.Function;

/**
 * Item event group (server and client scripts): right-click, tooltip, pickup, drop, entity
 * interaction, finished use, and runtime property modification, dispatched by {@link Item}.
 */
@Doc("Item event group for server and client scripts (right-click, tooltip, pickup, drop, interaction, food, and modification).")
public interface ItemEvents {
    /** Event group published as {@code ItemEvents} in the versioned and stable event modules. */
    EventGroup GROUP = EventGroup.of("ItemEvents");

    /** Fired on the server when a player right-clicks while holding an item. Dispatched by item. */
    @Doc("Fired on the server when a player right-clicks with an item (dispatched by item id). Return true to consume.")
    EventBusJS<PlayerInteractEvent.RightClickItem, Item> RIGHT_CLICKED =
            GROUP.server(
                    "rightClicked",
                    PlayerInteractEvent.RightClickItem.class,
                    dispatchByItem(PlayerInteractEvent::getItemStack),
                    api -> {
                        api.versioned();
                        api.stable()
                                .when(event -> ServerLevelEventThread.isCurrent(event.getLevel()))
                                .view(
                                        StableItemEvent.class,
                                        e -> NeoForgeStableEventProjections.itemStackEvent(
                                                e.getItemStack(), e.getEntity(), e.getLevel()))
                                .selector(String.class, NeoForgeStableEventProjections::resolveItemSelector)
                                .cancellable()
                                .scriptTypes(ScriptType.SERVER);
                    });

    /**
     * Runtime item property modification (server scripts, posted-object mode). Posted at server
     * startup and during {@code /nekojs reload server}: events are collected into an inert plan,
     * and at commit time the baseline is restored before the plan is applied (see
     * {@link ItemModificationEventJS}). Not registered on the NeoForge bus.
     */
    @Doc("Runtime item property modification event (food, tool, attack damage, attack speed).")
    EventBusJS<ItemModificationEventJS, Void> MODIFICATION =
            GROUP.server("modification", ItemModificationEventJS.class, api -> api.versioned());

    /** Fired on the client when an item tooltip is built. Dispatched by item. */
    @Doc("Fired on the client when an item tooltip is rendered (dispatched by item id).")
    EventBusJS<ItemTooltipEvent, Item> TOOLTIP =
            GROUP.client(
                    "tooltip",
                    ItemTooltipEvent.class,
                    dispatchByItem(ItemTooltipEvent::getItemStack),
                    api -> {
                        api.versioned();
                        api.stable()
                                .when(event -> ClientEventThread.isCurrent())
                                .view(StableItemEvent.class, NeoForgeStableEventProjections::itemTooltip)
                                .selector(String.class, NeoForgeStableEventProjections::resolveItemSelector)
                                .scriptTypes(ScriptType.CLIENT);
                    });

    /** Fired before a player picks up an item entity. Dispatched by item. */
    @Doc("Fired before a player picks up an item entity (dispatched by item id). Return true to prevent pickup.")
    EventBusJS<ItemEntityPickupEvent.Pre, Item> CAN_PICK_UP =
            GROUP.add(
                    "canPickUp",
                    ScriptType.SERVER,
                    EventBusJS.of(ItemEntityPickupEvent.Pre.class, true, dispatchByPickupItem()),
                    api -> {
                        api.versioned();
                        api.stable()
                                .when(event -> ServerLevelEventThread.isCurrent(event.getPlayer().level())
                                        && !event.canPickup().isFalse())
                                .view(
                                        StableItemEvent.class,
                                        e -> NeoForgeStableEventProjections.itemStackEvent(
                                                e.getItemEntity().getItem(), e.getPlayer(), e.getPlayer().level()))
                                .selector(String.class, NeoForgeStableEventProjections::resolveItemSelector)
                                .cancellable()
                                .scriptTypes(ScriptType.SERVER);
                    });

    /**
     * Deprecated alias for {@link #CAN_PICK_UP}. Use {@code canPickUp} for cross-loader
     * compatibility.
     */
    // pickedUpPre: a redundant alias for canPickUp (the same Pre event). The cross-platform main
    // names are canPickUp/pickedUp (some loaders have no separate Pre event), so scripts should
    // migrate to canPickUp.
    @Deprecated
    @Doc("Deprecated alias of canPickUp. Fired before a player picks up an item entity (dispatched by item id).")
    EventBusJS<ItemEntityPickupEvent.Pre, Item> PICKED_UP_PRE =
            GROUP.server("pickedUpPre", ItemEntityPickupEvent.Pre.class, dispatchByPickupItem(), api -> api.versioned());

    /** Fired after a player picks up an item entity. Dispatched by item. */
    @Doc("Fired after a player picks up an item entity (dispatched by item id). Not cancellable.")
    EventBusJS<ItemEntityPickupEvent.Post, Item> PICKED_UP =
            GROUP.server(
                    "pickedUp",
                    ItemEntityPickupEvent.Post.class,
                    dispatchByItem(ItemEntityPickupEvent.Post::getOriginalStack),
                    api -> {
                        api.versioned();
                        api.stable()
                                .when(event -> ServerLevelEventThread.isCurrent(event.getPlayer().level()))
                                .view(StableItemEvent.class, ItemEvents::pickedUpSnapshot)
                                .selector(String.class, NeoForgeStableEventProjections::resolveItemSelector)
                                .scriptTypes(ScriptType.SERVER);
                    });

    /** Fired when a player tosses an item from their inventory. Dispatched by item. */
    @Doc("Fired when a player drops/tosses an item (dispatched by item id). Return true to cancel.")
    EventBusJS<ItemTossEvent, Item> DROPPED =
            GROUP.server(
                    "dropped",
                    ItemTossEvent.class,
                    dispatchByItem(event -> event.getEntity().getItem()),
                    api -> {
                        api.versioned();
                        api.stable()
                                .when(event -> ServerLevelEventThread.isCurrent(event.getPlayer().level()))
                                .view(
                                        StableItemEvent.class,
                                        e -> NeoForgeStableEventProjections.itemStackEvent(
                                                e.getEntity().getItem(), e.getPlayer(), e.getPlayer().level()))
                                .selector(String.class, NeoForgeStableEventProjections::resolveItemSelector)
                                .cancellable()
                                .scriptTypes(ScriptType.SERVER);
                    });

    /** Fired on the server when a player right-clicks an entity while holding an item. Dispatched by item. */
    @Doc("Fired on the server when a player right-clicks an entity with an item (dispatched by item id). Return true to cancel.")
    EventBusJS<PlayerInteractEvent.EntityInteract, Item> ENTITY_INTERACTED =
            GROUP.server(
                    "entityInteracted",
                    PlayerInteractEvent.EntityInteract.class,
                    dispatchByItem(PlayerInteractEvent.EntityInteract::getItemStack),
                    api -> {
                        api.versioned();
                        api.stable()
                                .when(event -> event.getEntity() instanceof ServerPlayer
                                        && ServerLevelEventThread.isCurrent(event.getLevel()))
                                .view(
                                        StableItemEvent.class,
                                        e -> NeoForgeStableEventProjections.itemStackEvent(
                                                e.getItemStack(), e.getEntity(), e.getLevel(), e.getTarget()))
                                .selector(String.class, NeoForgeStableEventProjections::resolveItemSelector)
                                .cancellable()
                                .scriptTypes(ScriptType.SERVER);
                    });

    /**
     * Fired when a living entity finishes using an item (food eaten, potion drunk, bow drawn,
     * etc.). Scripts can check {@code event.isFood()} or {@code event.item.isEdible()} to filter
     * to foods. Dispatched by item.
     */
    // Item use finished (finished eating food, using up a shield or bow, and so on). Matches KubeJS
    // foodEaten and is based on LivingEntityUseItemEvent.Finish. Scripts can tell food apart with
    // event.item.isEdible() and similar checks.
    @Doc("Fired when a living entity finishes using an item, such as eating food (dispatched by item id).")
    EventBusJS<LivingEntityUseItemEvent.Finish, Item> FOOD_EATEN =
            GROUP.server(
                    "foodEaten",
                    LivingEntityUseItemEvent.Finish.class,
                    dispatchByItem(LivingEntityUseItemEvent::getItem),
                    api -> {
                        api.versioned();
                        api.stable()
                                .when(e -> ServerLevelEventThread.isCurrent(e.getEntity().level()))
                                .view(
                                        StableItemEvent.class,
                                        e -> NeoForgeStableEventProjections.itemStackEvent(
                                                e.getItem(), e.getEntity(), e.getEntity().level()))
                                .selector(String.class, NeoForgeStableEventProjections::resolveItemSelector)
                                .scriptTypes(ScriptType.SERVER);
                    });

    private static StableItemEvent pickedUpSnapshot(ItemEntityPickupEvent.Post event) {
        ItemStack pickedUp = event.getOriginalStack();
        pickedUp.shrink(event.getCurrentStack().getCount());
        return NeoForgeStableEventProjections.itemStackEvent(
                pickedUp, event.getPlayer(), event.getPlayer().level());
    }

    private static <T> DispatchKey<T, Item> dispatchByItem(Function<T, ItemStack> toStack) {
        return DispatchKey.of(Item.class, toStack.andThen(ItemStack::getItem));
    }

    private static <T extends ItemEntityPickupEvent> DispatchKey<T, Item> dispatchByPickupItem() {
        return dispatchByItem(event -> event.getItemEntity().getItem());
    }

    /** Native NeoForge event bus bridge for item events. */
    EventBusForgeBridge FORGE_BRIDGE = EventBusForgeBridge.create(NeoForge.EVENT_BUS)
            // RightClickItem fires on both logical sides: the SERVER bus delivers only server instances
            // (client interaction runs on the Render thread).
            .bind(RIGHT_CLICKED, e -> !e.getLevel().isClientSide())
            .bind(TOOLTIP)
            .bind(PICKED_UP_PRE)
            .bindCancellable(CAN_PICK_UP, NeoForgeStageGateSubscriber::denyItemPickup)
            .bind(PICKED_UP)
            .bind(DROPPED)
            .bind(ENTITY_INTERACTED, e -> !e.getLevel().isClientSide())
            .bind(FOOD_EATEN, e -> e.getEntity().level() instanceof ServerLevel);
}
//?}
