// Fabric copy of the shared tree's ItemEvents interface, which is guarded file-wide by
// `//? if neoforge` and passes bus calls straight to the native NeoForge event classes. On
// Fabric, rightClicked comes from FabricItemEventBindings (each group instance registers on its
// own, and groups with the same name merge), and tooltip is delivered by
// FabricClientEventBindings through ItemTooltipCallback. canPickUp/pickedUpPre/pickedUp/dropped/
// foodEaten/entityInteracted are a batch of mixin hooks (FabricItemEventBindingsV2 +
// fabric/mixin/*, neutral payloads).
package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.event.DispatchKey;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.wrapper.event.item.ItemDroppedEventJS;
import com.tkisor.nekojs.wrapper.event.item.ItemEntityPickupEventJS;
import com.tkisor.nekojs.wrapper.event.item.ItemTooltipEventJS;
import com.tkisor.nekojs.wrapper.event.item.ItemUseFinishedEventJS;
import com.tkisor.nekojs.wrapper.event.item.PlayerEntityInteractEventJS;
import com.tkisor.nekojs.wrapper.event.server.ItemModificationEventJS;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Item event group for server and client scripts: right click, tooltip, pick up, drop, interact
 * and finished use. Dispatched by item. */
public interface ItemEvents {
    EventGroup GROUP = EventGroup.of("ItemEvents");

    /** Tooltip (client scripts): dispatched by item id. The lines list is mutable, and changes take
     * effect immediately. */
    EventBusJS<ItemTooltipEventJS, Item> TOOLTIP =
            GROUP.client("tooltip", ItemTooltipEventJS.class,
                    DispatchKey.of(Item.class, event -> event.getItemStack().getItem()));

    // ---- Server side: mixin hook batch (see FabricItemEventBindingsV2 / fabric/mixin/*) ----

    /** Pick-up check before it happens (server scripts): dispatched by item id; returning true blocks
     * this pick-up. */
    EventBusJS<ItemEntityPickupEventJS, Item> CAN_PICK_UP =
            GROUP.add("canPickUp", ScriptType.SERVER, EventBusJS.of(
                    ItemEntityPickupEventJS.class, true,
                    DispatchKey.of(Item.class, event -> event.getItem().getItem())));

    /** Deprecated alias for the same pre pick-up event as canPickUp; canPickUp is the main name
     * across platforms. */
    @Deprecated
    EventBusJS<ItemEntityPickupEventJS, Item> PICKED_UP_PRE =
            GROUP.add("pickedUpPre", ScriptType.SERVER, EventBusJS.of(
                    ItemEntityPickupEventJS.class, true,
                    DispatchKey.of(Item.class, event -> event.getItem().getItem())));

    /** Successful pick-up (server scripts): dispatched by item id. Notification only; cannot be
     * cancelled. */
    EventBusJS<ItemEntityPickupEventJS, Item> PICKED_UP =
            GROUP.server("pickedUp", ItemEntityPickupEventJS.class,
                    DispatchKey.of(Item.class, event -> event.getItem().getItem()));

    /** Player drops an item (server scripts): dispatched by item id. Notification only; Fabric cannot
     * cancel it. */
    EventBusJS<ItemDroppedEventJS, Item> DROPPED =
            GROUP.server("dropped", ItemDroppedEventJS.class,
                    DispatchKey.of(Item.class, event -> event.getItem().getItem()));

    /** Finished using an item (finished eating food, using up a shield or bow, and so on): dispatched
     * by item id; {@code event.isFood()} tells whether the item is food. */
    EventBusJS<ItemUseFinishedEventJS, Item> FOOD_EATEN =
            GROUP.server("foodEaten", ItemUseFinishedEventJS.class,
                    DispatchKey.of(Item.class, event -> event.getItem().getItem()));

    /** Player right-clicks an entity (server scripts): dispatched by the main-hand item id at
     * interaction time; returning true cancels the interaction. */
    EventBusJS<PlayerEntityInteractEventJS, Item> ENTITY_INTERACTED =
            GROUP.add("entityInteracted", ScriptType.SERVER, EventBusJS.of(
                    PlayerEntityInteractEventJS.class, true,
                    DispatchKey.of(Item.class, event -> event.getItem().getItem())));

    // modification (posted-object mode): the platform domain owner posts it at the server startup
    // collection point (about-to-start) and during the candidate DOMAIN_PLAN phase of a SERVER
    // reload. The events collect into an inert plan and the Adapter applies them at the commit
    // point; no bus event is involved.
    EventBusJS<ItemModificationEventJS, Void> MODIFICATION =
            GROUP.server("modification", ItemModificationEventJS.class);
}
