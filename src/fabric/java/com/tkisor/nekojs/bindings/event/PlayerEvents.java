// Fabric copy of PlayerEvents (the shared tree's copy is guarded by `//? if neoforge`).
// Holds the mixin-backed player events on Fabric; loggedIn/loggedOut/chat/tickPre/tickPost/
// cloned/respawned are supplied by FabricServerEventBindings and merged by EventGroupRegistry.
package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.event.DispatchKey;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.api.event.StablePlayerAdvancementEvent;
import com.tkisor.nekojs.api.event.StablePlayerDimensionChangeEvent;
import com.tkisor.nekojs.api.event.StablePlayerEvent;
import com.tkisor.nekojs.api.event.StablePlayerItemEvent;
import com.tkisor.nekojs.fabric.event.FabricStableEventProjections;
import com.tkisor.nekojs.wrapper.event.player.InventoryChangedEventJS;
import com.tkisor.nekojs.wrapper.event.player.PlayerAdvancementEventJS;
import com.tkisor.nekojs.wrapper.event.player.PlayerChangedDimensionEventJS;
import com.tkisor.nekojs.wrapper.event.player.PlayerContainerEventJS;
import com.tkisor.nekojs.wrapper.event.player.PlayerCraftedEventJS;
import com.tkisor.nekojs.wrapper.event.player.PlayerDestroyItemEventJS;
import com.tkisor.nekojs.fabric.event.FabricPlayerEventBindings;
import com.tkisor.nekojs.wrapper.event.player.PlayerEntityInteractEventJS;
import net.minecraft.world.item.Item;

/**
 * Player event group for server scripts (Fabric mixin subset): container open/close,
 * crafting/smelting, item break, advancement, entity interaction, dimension change, and
 * inventory change.
 */
@Doc("Player event group for server scripts: containers, crafting, smelting, item break, advancements, entity interaction, dimension changes, and inventory updates.")
public interface PlayerEvents {
    /** Event group published as {@code PlayerEvents}. */
    EventGroup GROUP = EventGroup.of("PlayerEvents");

    /** Fired when a player opens a container menu. */
    @Doc("Fired on the server when a player opens a container menu. Only visible in server_scripts.")
    EventBusJS<PlayerContainerEventJS, Void> CONTAINER_OPENED =
            GROUP.server("containerOpened", PlayerContainerEventJS.class, api -> {
                api.versioned();
                api.stable()
                        .when(event -> ServerLevelEventThread.isCurrent(event.getPlayer().level()))
                        .view(StablePlayerEvent.class, FabricStableEventProjections::playerContainer)
                        .scriptTypes(ScriptType.SERVER);
            });

    /** Deprecated alias for {@link #CONTAINER_OPENED}; prefer {@code containerOpened}. */
    @Deprecated
    @Doc("Deprecated alias for containerOpened; prefer PlayerEvents.containerOpened.")
    EventBusJS<PlayerContainerEventJS, Void> INVENTORY_OPENED =
            GROUP.server("inventoryOpened", PlayerContainerEventJS.class, api -> api.versioned());

    /** Fired when a player closes a container menu. */
    @Doc("Fired on the server when a player closes a container menu. Only visible in server_scripts.")
    EventBusJS<PlayerContainerEventJS, Void> CONTAINER_CLOSED =
            GROUP.server("containerClosed", PlayerContainerEventJS.class, api -> {
                api.versioned().when(event -> !FabricPlayerEventBindings.isStableContainerClose(event));
                api.stable()
                        .when(event -> FabricPlayerEventBindings.isStableContainerClose(event)
                                && ServerLevelEventThread.isCurrent(event.getPlayer().level()))
                        .view(StablePlayerEvent.class, FabricStableEventProjections::playerContainer)
                        .scriptTypes(ScriptType.SERVER);
            });

    /** Deprecated alias for {@link #CONTAINER_CLOSED}; prefer {@code containerClosed}. */
    @Deprecated
    @Doc("Deprecated alias for containerClosed; prefer PlayerEvents.containerClosed.")
    EventBusJS<PlayerContainerEventJS, Void> INVENTORY_CLOSED =
            GROUP.server("inventoryClosed", PlayerContainerEventJS.class, api -> api.versioned());

    /** Fired when a player crafts an item; dispatched by the resulting item. */
    @Doc("Fired on the server when a player crafts an item; dispatched by the resulting item. Only visible in server_scripts.")
    EventBusJS<PlayerCraftedEventJS, Item> CRAFTED =
            GROUP.server("crafted", PlayerCraftedEventJS.class,
                    DispatchKey.of(Item.class, e -> e.getResult().getItem()), api -> {
                        api.versioned().when(event -> !FabricPlayerEventBindings.isStableResultTake(event));
                        api.stable()
                                .when(PlayerEvents::isCurrentStableResultTake)
                                .view(StablePlayerItemEvent.class, FabricStableEventProjections::playerCrafted)
                                .selector(String.class, FabricStableEventProjections::resolveItemSelector)
                                .scriptTypes(ScriptType.SERVER);
                    });

    /** Fired when a player takes a smelted item from a furnace; dispatched by the resulting item. */
    @Doc("Fired on the server when a player takes a smelted result; dispatched by the resulting item. Only visible in server_scripts.")
    EventBusJS<PlayerCraftedEventJS, Item> SMELTED =
            GROUP.server("smelted", PlayerCraftedEventJS.class,
                    DispatchKey.of(Item.class, e -> e.getResult().getItem()), api -> {
                        api.versioned().when(event -> !FabricPlayerEventBindings.isStableResultTake(event));
                        api.stable()
                                .when(PlayerEvents::isCurrentStableResultTake)
                                .view(StablePlayerItemEvent.class, FabricStableEventProjections::playerCrafted)
                                .selector(String.class, FabricStableEventProjections::resolveItemSelector)
                                .scriptTypes(ScriptType.SERVER);
                    });

    /** Fired when a player's item breaks from durability loss; dispatched by item. */
    @Doc("Fired on the server when a player's item breaks; dispatched by item. Only visible in server_scripts.")
    EventBusJS<PlayerDestroyItemEventJS, Item> DESTROYED =
            GROUP.server("destroyed", PlayerDestroyItemEventJS.class,
                    DispatchKey.of(Item.class, e -> e.getItemStack().getItem()), api -> {
                        api.versioned().when(event -> FabricPlayerEventBindings.isDestructionPublished(event, false));
                        api.stable()
                                .when(event -> FabricPlayerEventBindings.isDestructionPublished(event, true)
                                        && ServerLevelEventThread.isCurrent(event.getPlayer().level()))
                                .view(StablePlayerItemEvent.class, FabricStableEventProjections::playerDestroyed)
                                .selector(String.class, FabricStableEventProjections::resolveItemSelector)
                                .scriptTypes(ScriptType.SERVER);
                    });

    /** Fired when a player earns an advancement. */
    @Doc("Fired on the server when a player earns an advancement. Only visible in server_scripts.")
    EventBusJS<PlayerAdvancementEventJS, Void> ADVANCEMENT =
            GROUP.server("advancement", PlayerAdvancementEventJS.class, api -> {
                api.versioned().when(event -> !FabricPlayerEventBindings.isStableAdvancement(event));
                api.stable()
                        .when(FabricPlayerEventBindings::isStableAdvancement)
                        .view(StablePlayerAdvancementEvent.class, FabricStableEventProjections::playerAdvancement)
                        .scriptTypes(ScriptType.SERVER);
            });

    /** Fired when a player interacts with an entity; returning true cancels the interaction. */
    @Doc("Fired on the server when a player interacts with an entity.")
    @Doc("Cancellable: return true to cancel interaction packet handling. Only visible in server_scripts.")
    EventBusJS<PlayerEntityInteractEventJS, Void> ENTITY_INTERACT =
            GROUP.add("entityInteract", ScriptType.SERVER,
                    EventBusJS.of(PlayerEntityInteractEventJS.class, true), api -> {
                        api.versioned().when(event -> !FabricPlayerEventBindings.isStableInteraction(event));
                        api.stable()
                                .when(FabricPlayerEventBindings::isStableInteraction)
                                .view(StablePlayerEvent.class, FabricStableEventProjections::playerEntityInteract)
                                .cancellable()
                                .scriptTypes(ScriptType.SERVER);
                    });

    /** Fired when a player changes dimensions. */
    @Doc("Fired on the server when a player changes dimensions. Only visible in server_scripts.")
    EventBusJS<PlayerChangedDimensionEventJS, Void> CHANGED_DIMENSION =
            GROUP.server("changedDimension", PlayerChangedDimensionEventJS.class, api -> {
                api.versioned();
                api.stable()
                        .when(event -> event.getPlayer() != null && ServerLevelEventThread.isCurrent(event.getPlayer().level())
                                && event.getFrom() != null && event.getTo() != null
                                && !event.getFrom().equals(event.getTo()))
                        .view(StablePlayerDimensionChangeEvent.class, FabricStableEventProjections::playerChangedDimension)
                        .scriptTypes(ScriptType.SERVER);
            });

    /** Fired when a player's inventory slot changes; dispatched by item. */
    @Doc("Fired on the server when an item in a player's inventory changes; dispatched by item. Only visible in server_scripts.")
    EventBusJS<InventoryChangedEventJS, Item> INVENTORY_CHANGED =
            GROUP.server("inventoryChanged", InventoryChangedEventJS.class,
                    DispatchKey.of(Item.class, e -> e.getItem().getItem()), api -> api.versioned());

    private static boolean isCurrentStableResultTake(PlayerCraftedEventJS event) {
        return FabricPlayerEventBindings.isStableResultTake(event)
                && ServerLevelEventThread.isCurrent(event.getPlayer().level());
    }
}
