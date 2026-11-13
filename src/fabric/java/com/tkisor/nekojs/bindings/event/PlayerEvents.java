// Fabric copy of the shared tree's PlayerEvents interface, which is guarded file-wide by
// `//? if neoforge` and passes bus calls straight to the native NeoForge event classes. This
// copy holds only the player events that mixins provide on Fabric; payloads are the shared
// tree's neutral classes (wrapper/event/player). loggedIn/loggedOut/chat/tickPre/tickPost/cloned/
// respawned already come from FabricServerEventBindings.PLAYER_EVENTS and are not declared here
// again: EventGroupRegistry merges groups with the same name "PlayerEvents" (the same mechanism
// as the ServerEvents copy), so scripts see the same group and bus names as on NeoForge. The
// shared tree version merges back into a single copy once its event payloads are neutral.
package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.event.DispatchKey;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.wrapper.event.player.InventoryChangedEventJS;
import com.tkisor.nekojs.wrapper.event.player.PlayerAdvancementEventJS;
import com.tkisor.nekojs.wrapper.event.player.PlayerChangedDimensionEventJS;
import com.tkisor.nekojs.wrapper.event.player.PlayerContainerEventJS;
import com.tkisor.nekojs.wrapper.event.player.PlayerCraftedEventJS;
import com.tkisor.nekojs.wrapper.event.player.PlayerDestroyItemEventJS;
import com.tkisor.nekojs.wrapper.event.player.PlayerEntityInteractEventJS;
import net.minecraft.world.item.Item;

/** The Fabric mixin-provided subset of the player event group for server scripts: container open
 * and close, crafting/smelting/item break, advancement, entity interaction and dimension change. */
public interface PlayerEvents {
    EventGroup GROUP = EventGroup.of("PlayerEvents");

    EventBusJS<PlayerContainerEventJS, Void> CONTAINER_OPENED =
            GROUP.server("containerOpened", PlayerContainerEventJS.class);
    // inventoryOpened: a cross-version compatibility alias for containerOpened. Scripts should
    // migrate to containerOpened.
    @Deprecated
    EventBusJS<PlayerContainerEventJS, Void> INVENTORY_OPENED =
            GROUP.server("inventoryOpened", PlayerContainerEventJS.class);
    EventBusJS<PlayerContainerEventJS, Void> CONTAINER_CLOSED =
            GROUP.server("containerClosed", PlayerContainerEventJS.class);
    // inventoryClosed: a cross-version compatibility alias for containerClosed. Scripts should
    // migrate to containerClosed.
    @Deprecated
    EventBusJS<PlayerContainerEventJS, Void> INVENTORY_CLOSED =
            GROUP.server("inventoryClosed", PlayerContainerEventJS.class);

    // crafted / smelted: dispatched by the resulting item id (the same dispatchByItem scheme as
    // PlayerEvents on NeoForge)
    EventBusJS<PlayerCraftedEventJS, Item> CRAFTED =
            GROUP.server("crafted", PlayerCraftedEventJS.class,
                    DispatchKey.of(Item.class, e -> e.getResult().getItem()));
    EventBusJS<PlayerCraftedEventJS, Item> SMELTED =
            GROUP.server("smelted", PlayerCraftedEventJS.class,
                    DispatchKey.of(Item.class, e -> e.getResult().getItem()));
    EventBusJS<PlayerDestroyItemEventJS, Item> DESTROYED =
            GROUP.server("destroyed", PlayerDestroyItemEventJS.class,
                    DispatchKey.of(Item.class, e -> e.getItemStack().getItem()));

    EventBusJS<PlayerAdvancementEventJS, Void> ADVANCEMENT =
            GROUP.server("advancement", PlayerAdvancementEventJS.class);

    // entityInteract: on NeoForge this is an ICancellableEvent. Fabric has no ICancellableEvent
    // check chain (the default cancellability predicate of EventGroup.server is always false on
    // Fabric), so the bus is created explicitly as cancellable. A listener returning true cancels
    // handling of ServerboundInteractPacket, so interactOn does not run.
    EventBusJS<PlayerEntityInteractEventJS, Void> ENTITY_INTERACT =
            GROUP.add("entityInteract", ScriptType.SERVER,
                    EventBusJS.of(PlayerEntityInteractEventJS.class, true));

    EventBusJS<PlayerChangedDimensionEventJS, Void> CHANGED_DIMENSION =
            GROUP.server("changedDimension", PlayerChangedDimensionEventJS.class);

    // inventoryChanged: dispatched by item id, the same declaration as in the shared tree on
    // NeoForge (listener attachment is in FabricPlayerEventBindings.registerLifecycle).
    EventBusJS<InventoryChangedEventJS, Item> INVENTORY_CHANGED =
            GROUP.server("inventoryChanged", InventoryChangedEventJS.class,
                    DispatchKey.of(Item.class, e -> e.getItem().getItem()));
}
