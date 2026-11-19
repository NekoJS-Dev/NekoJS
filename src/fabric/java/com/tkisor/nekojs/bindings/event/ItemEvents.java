// Fabric copy of ItemEvents (the shared tree's copy is guarded by `//? if neoforge`).
// On Fabric, rightClicked is provided by FabricItemEventBindings, tooltip by
// FabricClientEventBindings (ItemTooltipCallback), and the server-side hooks below by
// FabricItemEventBindingsV2 + fabric mixins. EventGroupRegistry merges groups sharing "ItemEvents".
package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.event.DispatchKey;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.api.event.StableItemEvent;
import com.tkisor.nekojs.client.ClientEventThread;
import com.tkisor.nekojs.fabric.event.FabricItemEventBindingsV2;
import com.tkisor.nekojs.fabric.event.FabricStableEventProjections;
import com.tkisor.nekojs.wrapper.event.item.ItemDroppedEventJS;
import com.tkisor.nekojs.wrapper.event.item.ItemEntityPickupEventJS;
import com.tkisor.nekojs.wrapper.event.item.ItemTooltipEventJS;
import com.tkisor.nekojs.wrapper.event.item.ItemUseFinishedEventJS;
import com.tkisor.nekojs.wrapper.event.item.PlayerEntityInteractEventJS;
import com.tkisor.nekojs.wrapper.event.server.ItemModificationEventJS;
import net.minecraft.world.item.Item;

/**
 * Item event group for server and client scripts (Fabric): tooltip, pick-up, drop,
 * finished use, entity interaction, and item modification. Dispatched by item.
 */
@Doc("Item event group for client and server scripts: tooltips, pick-up, drop, finished use, entity interaction, and item modification.")
public interface ItemEvents {
    /** Event group published as {@code ItemEvents}. */
    EventGroup GROUP = EventGroup.of("ItemEvents");

    /** Tooltip (client scripts): dispatched by item id; the lines list is mutable. */
    @Doc("Fired on the client when an item tooltip is built; dispatched by item.")
    @Doc("The tooltip lines list is mutable and changes take effect immediately. Only visible in client_scripts.")
    EventBusJS<ItemTooltipEventJS, Item> TOOLTIP =
            GROUP.client("tooltip", ItemTooltipEventJS.class,
                    DispatchKey.of(Item.class, event -> event.getItemStack().getItem()), api -> {
                        api.versioned();
                        api.stable()
                                .when(event -> ClientEventThread.isCurrent())
                                .view(StableItemEvent.class, FabricStableEventProjections::itemTooltip)
                                .selector(String.class, FabricStableEventProjections::resolveItemSelector)
                                .scriptTypes(ScriptType.CLIENT);
                    });

    /** Pick-up check before it happens (server scripts): dispatched by item id. */
    @Doc("Fired on the server before a player picks up an item entity; dispatched by item.")
    @Doc("Cancellable: return true to block the pick-up. Only visible in server_scripts.")
    EventBusJS<ItemEntityPickupEventJS, Item> CAN_PICK_UP =
            GROUP.add("canPickUp", ScriptType.SERVER, EventBusJS.of(
                    ItemEntityPickupEventJS.class, true,
                    DispatchKey.of(Item.class, event -> event.getItem().getItem())), api -> {
                api.versioned();
                api.stable()
                        .when(event -> ServerLevelEventThread.isCurrent(event.getPlayer().level()))
                        .view(StableItemEvent.class, FabricStableEventProjections::itemPickup)
                        .selector(String.class, FabricStableEventProjections::resolveItemSelector)
                        .cancellable()
                        .scriptTypes(ScriptType.SERVER);
            });

    /** Deprecated alias for {@link #CAN_PICK_UP}; prefer {@code canPickUp}. */
    @Deprecated
    @Doc("Deprecated alias for canPickUp; prefer ItemEvents.canPickUp.")
    EventBusJS<ItemEntityPickupEventJS, Item> PICKED_UP_PRE =
            GROUP.add("pickedUpPre", ScriptType.SERVER, EventBusJS.of(
                    ItemEntityPickupEventJS.class, true,
                    DispatchKey.of(Item.class, event -> event.getItem().getItem())), api -> api.versioned());

    /** Successful pick-up (server scripts): dispatched by item id (notification only). */
    @Doc("Fired on the server after a player picks up an item entity; dispatched by item.")
    @Doc("Notification only; cannot be cancelled. Only visible in server_scripts.")
    EventBusJS<ItemEntityPickupEventJS, Item> PICKED_UP =
            GROUP.server("pickedUp", ItemEntityPickupEventJS.class,
                    DispatchKey.of(Item.class, event -> event.pickedUpStack().getItem()), api -> {
                        api.versioned().when(event -> !FabricItemEventBindingsV2.isStablePickupPost(event));
                        api.stable()
                                .when(event -> FabricItemEventBindingsV2.isStablePickupPost(event)
                                        && ServerLevelEventThread.isCurrent(event.getPlayer().level()))
                                .view(StableItemEvent.class, event -> FabricStableEventProjections.itemStackEvent(
                                        event.pickedUpStack(), event.getPlayer(), event.getPlayer().level()))
                                .selector(String.class, FabricStableEventProjections::resolveItemSelector)
                                .scriptTypes(ScriptType.SERVER);
                    });

    /** Player drops an item (server scripts): dispatched by item id; returning true cancels spawning the dropped item entity. */
    @Doc("Fired on the server when a player drops an item; dispatched by item.")
    @Doc("Cancellable: return true to prevent spawning the dropped item entity. Only visible in server_scripts.")
    EventBusJS<ItemDroppedEventJS, Item> DROPPED =
            GROUP.add("dropped", ScriptType.SERVER, EventBusJS.of(
                    ItemDroppedEventJS.class, true,
                    DispatchKey.of(Item.class, event -> event.getItem().getItem())), api -> {
                api.versioned().when(event -> !FabricItemEventBindingsV2.isPlayerToss(event));
                api.stable()
                        .when(event -> FabricItemEventBindingsV2.isPlayerToss(event)
                                && ServerLevelEventThread.isCurrent(event.getPlayer().level()))
                        .view(StableItemEvent.class, FabricStableEventProjections::itemDropped)
                        .selector(String.class, FabricStableEventProjections::resolveItemSelector)
                        .cancellable()
                        .scriptTypes(ScriptType.SERVER);
            });

    /** Finished using an item (food, shield, bow, etc.): dispatched by item id. */
    @Doc("Fired on the server when a living entity finishes using an item (eating food, drinking, etc.); dispatched by item.")
    @Doc("Use event.isFood() to check whether the item is food. Only visible in server_scripts.")
    EventBusJS<ItemUseFinishedEventJS, Item> FOOD_EATEN =
            GROUP.server("foodEaten", ItemUseFinishedEventJS.class,
                    DispatchKey.of(Item.class, event -> event.getItem().getItem()), api -> {
                        api.versioned();
                        api.stable()
                                .when(event -> event.getEntity() != null && ServerLevelEventThread.isCurrent(event.getEntity().level()))
                                .view(StableItemEvent.class, FabricStableEventProjections::itemUseFinished)
                                .selector(String.class, FabricStableEventProjections::resolveItemSelector)
                                .scriptTypes(ScriptType.SERVER);
                    });

    /** Player right-clicks an entity (server scripts): dispatched by the main-hand item id. */
    @Doc("Fired on the server when a player right-clicks an entity; dispatched by the main-hand item.")
    @Doc("Cancellable: return true to cancel the interaction. Only visible in server_scripts.")
    EventBusJS<PlayerEntityInteractEventJS, Item> ENTITY_INTERACTED =
            GROUP.add("entityInteracted", ScriptType.SERVER, EventBusJS.of(
                    PlayerEntityInteractEventJS.class, true,
                    DispatchKey.of(Item.class, event -> event.getItem().getItem())), api -> {
                api.versioned().when(event -> !FabricItemEventBindingsV2.isStableInteraction(event));
                api.stable()
                        .when(FabricItemEventBindingsV2::isStableInteraction)
                        .view(StableItemEvent.class, FabricStableEventProjections::itemEntityInteract)
                        .selector(String.class, FabricStableEventProjections::resolveItemSelector)
                        .cancellable()
                        .scriptTypes(ScriptType.SERVER);
            });

    /** Fired during server startup and reload to modify item properties. */
    @Doc("Fired during server startup and reload to modify registered item properties.")
    @Doc("Only visible in server_scripts.")
    EventBusJS<ItemModificationEventJS, Void> MODIFICATION =
            GROUP.server("modification", ItemModificationEventJS.class, api -> api.versioned());
}
