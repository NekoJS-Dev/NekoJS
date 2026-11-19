//? if neoforge {
package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.event.DispatchKey;
import com.tkisor.nekojs.api.event.EventBusForgeBridge;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.api.event.StablePlayerAdvancementEvent;
import com.tkisor.nekojs.api.event.StablePlayerChatEvent;
import com.tkisor.nekojs.api.event.StablePlayerDimensionChangeEvent;
import com.tkisor.nekojs.api.event.StablePlayerEvent;
import com.tkisor.nekojs.api.event.StablePlayerItemEvent;
import com.tkisor.nekojs.wrapper.event.player.InventoryChangedEventJS;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.player.*;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.function.Function;

/**
 * Player event group for server scripts: login and logout, chat, tick, respawn, crafting and
 * smelting, and inventory changes (crafting, smelting, destruction, and inventory events dispatch by item).
 */
@Doc("Player event group for server scripts: login/logout, chat, tick, respawn, crafting/smelting, and inventory changes.")
public interface PlayerEvents {
    EventGroup GROUP = EventGroup.of("PlayerEvents");

    /** Fired when a player logs in to the server. */
    @Doc("Fired when a player logs in to the server.")
    EventBusJS<PlayerEvent.PlayerLoggedInEvent, Void> LOGGED_IN =
            GROUP.server("loggedIn", PlayerEvent.PlayerLoggedInEvent.class, api -> {
                api.versioned();
                api.stable()
                        .when(PlayerEvents::isCurrentPlayerTransition)
                        .view(StablePlayerEvent.class, NeoForgeStableEventProjections::player)
                        .scriptTypes(ScriptType.SERVER);
            });

    /** Fired when a player logs out from the server. */
    @Doc("Fired when a player logs out from the server.")
    EventBusJS<PlayerEvent.PlayerLoggedOutEvent, Void> LOGGED_OUT =
            GROUP.server("loggedOut", PlayerEvent.PlayerLoggedOutEvent.class, api -> {
                api.versioned();
                api.stable()
                        .when(PlayerEvents::isCurrentPlayerTransition)
                        .view(StablePlayerEvent.class, NeoForgeStableEventProjections::player)
                        .scriptTypes(ScriptType.SERVER);
            });

    /** Fired when a player sends a chat message on the server. */
    @Doc("Fired when a player sends a chat message on the server.")
    EventBusJS<ServerChatEvent, Void> CHAT =
            GROUP.server("chat", ServerChatEvent.class, api -> {
                api.versioned();
                api.stable()
                        .when(NeoForgePlayerChatScope::isPacketChat)
                        .view(StablePlayerChatEvent.class, NeoForgeStableEventProjections::playerChat)
                        .cancellable()
                        .scriptTypes(ScriptType.SERVER);
            });

    /** Fired at the end of each server-side player tick. */
    @Doc("Fired at the end of each server-side player tick.")
    EventBusJS<PlayerTickEvent.Post, Void> TICK_POST =
            GROUP.server("tickPost", PlayerTickEvent.Post.class, api -> {
                api.versioned();
                api.stable()
                        .when(PlayerEvents::isCurrentPlayerTransition)
                        .view(StablePlayerEvent.class, NeoForgeStableEventProjections::player)
                        .scriptTypes(ScriptType.SERVER);
            });

    /** Fired at the start of each server-side player tick. */
    @Doc("Fired at the start of each server-side player tick.")
    EventBusJS<PlayerTickEvent.Pre, Void> TICK_PRE =
            GROUP.server("tickPre", PlayerTickEvent.Pre.class, api -> {
                api.versioned();
                api.stable()
                        .when(PlayerEvents::isCurrentPlayerTransition)
                        .view(StablePlayerEvent.class, NeoForgeStableEventProjections::player)
                        .scriptTypes(ScriptType.SERVER);
            });

    /** Fired when a player entity is cloned (for example on respawn or dimension change). */
    @Doc("Fired when a player entity is cloned (e.g., on respawn or returning from The End).")
    EventBusJS<PlayerEvent.Clone, Void> CLONED =
            GROUP.server("cloned", PlayerEvent.Clone.class, api -> {
                api.versioned();
                api.stable()
                        .when(PlayerEvents::isCurrentPlayerTransition)
                        .view(StablePlayerEvent.class, NeoForgeStableEventProjections::player)
                        .scriptTypes(ScriptType.SERVER);
            });

    /** Fired after a player respawns. */
    @Doc("Fired after a player respawns.")
    EventBusJS<PlayerEvent.PlayerRespawnEvent, Void> RESPAWNED =
            GROUP.server("respawned", PlayerEvent.PlayerRespawnEvent.class, api -> {
                api.versioned();
                api.stable()
                        .when(PlayerEvents::isCurrentPlayerTransition)
                        .view(StablePlayerEvent.class, NeoForgeStableEventProjections::player)
                        .scriptTypes(ScriptType.SERVER);
            });

    /** Fired when a player changes dimensions. */
    @Doc("Fired when a player changes dimensions.")
    EventBusJS<PlayerEvent.PlayerChangedDimensionEvent, Void> CHANGED_DIMENSION =
            GROUP.server("changedDimension", PlayerEvent.PlayerChangedDimensionEvent.class, api -> {
                api.versioned();
                api.stable()
                        .when(event -> event.getEntity() instanceof ServerPlayer player
                                && ServerLevelEventThread.isCurrent(player.level())
                                && event.getFrom() != null && event.getTo() != null
                                && !event.getFrom().equals(event.getTo()))
                        .view(StablePlayerDimensionChangeEvent.class, NeoForgeStableEventProjections::playerChangedDimension)
                        .scriptTypes(ScriptType.SERVER);
            });

    /** Fired when a player earns an advancement. */
    @Doc("Fired when a player earns an advancement.")
    EventBusJS<AdvancementEvent.AdvancementEarnEvent, Void> ADVANCEMENT =
            GROUP.server("advancement", AdvancementEvent.AdvancementEarnEvent.class, api -> {
                api.versioned();
                api.stable()
                        .when(event -> event.getEntity() instanceof ServerPlayer player
                                && ServerLevelEventThread.isCurrent(player.level()))
                        .view(StablePlayerAdvancementEvent.class, NeoForgeStableEventProjections::playerAdvancement)
                        .scriptTypes(ScriptType.SERVER);
            });

    /** Fired when a player opens a container menu. */
    @Doc("Fired when a player opens a container menu.")
    EventBusJS<PlayerContainerEvent.Open, Void> CONTAINER_OPENED =
            GROUP.server("containerOpened", PlayerContainerEvent.Open.class, api -> {
                api.versioned().when(event -> !(event instanceof NeoForgeContainerEvents.NautilusOpen));
                api.stable()
                        .when(event -> event.getEntity() instanceof ServerPlayer player
                                && ServerLevelEventThread.isCurrent(player.level())
                                && player.containerMenu == event.getContainer())
                        .view(StablePlayerEvent.class, NeoForgeStableEventProjections::player)
                        .scriptTypes(ScriptType.SERVER);
            });

    /**
     * Cross-version compatibility alias for {@link #CONTAINER_OPENED}. Scripts should migrate to
     * {@code containerOpened}.
     */
    @Doc("Deprecated alias for containerOpened.")
    @Deprecated
    EventBusJS<PlayerContainerEvent.Open, Void> INVENTORY_OPENED =
            GROUP.server("inventoryOpened", PlayerContainerEvent.Open.class, api -> api.versioned());

    /** Fired when a player closes a container menu. */
    @Doc("Fired when a player closes a container menu.")
    EventBusJS<PlayerContainerEvent.Close, Void> CONTAINER_CLOSED =
            GROUP.server("containerClosed", PlayerContainerEvent.Close.class, api -> {
                api.versioned();
                api.stable()
                        .when(event -> NeoForgeContainerEvents.isStableClose(event)
                                && ServerLevelEventThread.isCurrent(event.getEntity().level()))
                        .view(StablePlayerEvent.class, NeoForgeStableEventProjections::player)
                        .scriptTypes(ScriptType.SERVER);
            });

    /**
     * Cross-version compatibility alias for {@link #CONTAINER_CLOSED}. Scripts should migrate to
     * {@code containerClosed}.
     */
    @Doc("Deprecated alias for containerClosed.")
    @Deprecated
    EventBusJS<PlayerContainerEvent.Close, Void> INVENTORY_CLOSED =
            GROUP.server("inventoryClosed", PlayerContainerEvent.Close.class, api -> api.versioned());

    /** Fired when a player interacts with an entity on the server. */
    @Doc("Fired when a player interacts with an entity on the server.")
    EventBusJS<PlayerInteractEvent.EntityInteract, Void> ENTITY_INTERACT =
            GROUP.server("entityInteract", PlayerInteractEvent.EntityInteract.class, api -> {
                api.versioned();
                api.stable()
                        .when(event -> event.getEntity() instanceof ServerPlayer
                                && ServerLevelEventThread.isCurrent(event.getLevel()))
                        .view(StablePlayerEvent.class, NeoForgeStableEventProjections::player)
                        .cancellable()
                        .scriptTypes(ScriptType.SERVER);
            });

    /** Fired when a player crafts an item; optionally dispatches by the crafted item id. */
    @Doc("Fired when a player crafts an item; optionally dispatches by the crafted item id.")
    EventBusJS<PlayerEvent.ItemCraftedEvent, Item> CRAFTED =
            GROUP.server(
                    "crafted",
                    PlayerEvent.ItemCraftedEvent.class,
                    dispatchByItem(PlayerEvent.ItemCraftedEvent::getCrafting),
                    api -> {
                        api.versioned();
                        api.stable()
                                .when(event -> event.getEntity() instanceof ServerPlayer player
                                        && ServerLevelEventThread.isCurrent(player.level()))
                                .view(
                                        StablePlayerItemEvent.class,
                                        e -> NeoForgeStableEventProjections.playerItem(e.getEntity(), e.getCrafting()))
                                .selector(String.class, NeoForgeStableEventProjections::resolveItemSelector)
                                .scriptTypes(ScriptType.SERVER);
                    });

    /** Fired when a player takes a smelted item from a furnace; optionally dispatches by item id. */
    @Doc("Fired when a player takes a smelted item from a furnace; optionally dispatches by item id.")
    EventBusJS<PlayerEvent.ItemSmeltedEvent, Item> SMELTED =
            GROUP.server(
                    "smelted",
                    PlayerEvent.ItemSmeltedEvent.class,
                    dispatchByItem(PlayerEvent.ItemSmeltedEvent::getSmelting),
                    api -> {
                        api.versioned();
                        api.stable()
                                .when(event -> event.getEntity() instanceof ServerPlayer player
                                        && ServerLevelEventThread.isCurrent(player.level())
                                        && NeoForgeSmeltingRouting.isStable(event))
                                .view(
                                        StablePlayerItemEvent.class,
                                        e -> NeoForgeStableEventProjections.playerItem(e.getEntity(), e.getSmelting()))
                                .selector(String.class, NeoForgeStableEventProjections::resolveItemSelector)
                                .scriptTypes(ScriptType.SERVER);
                    });

    /** Fired when a player's item breaks or is destroyed; optionally dispatches by item id. */
    @Doc("Fired when a player's item breaks or is destroyed; optionally dispatches by item id.")
    EventBusJS<PlayerDestroyItemEvent, Item> DESTROYED =
            GROUP.server(
                    "destroyed",
                    PlayerDestroyItemEvent.class,
                    dispatchByItem(PlayerDestroyItemEvent::getOriginal),
                    api -> {
                        api.versioned().when(NeoForgeShieldDestruction::isNative);
                        api.stable()
                                .when(event -> isCurrentPlayerTransition(event) && NeoForgeShieldDestruction.isStable(event))
                                .view(
                                        StablePlayerItemEvent.class,
                                        e -> NeoForgeStableEventProjections.playerItem(e.getEntity(), e.getOriginal()))
                                .selector(String.class, NeoForgeStableEventProjections::resolveItemSelector)
                                .scriptTypes(ScriptType.SERVER);
                    });

    /** Player inventory change: dispatched by item id ({@code PlayerEvents.inventoryChanged('minecraft:stone', ...)}). */
    @Doc("Fired when a player's inventory changes; optionally dispatches by item id.")
    EventBusJS<InventoryChangedEventJS, Item> INVENTORY_CHANGED =
            GROUP.server("inventoryChanged", InventoryChangedEventJS.class, dispatchByInventoryItem(), api -> api.versioned());

    private static boolean isCurrentPlayerTransition(PlayerEvent event) {
        return event.getEntity() instanceof ServerPlayer player && ServerLevelEventThread.isCurrent(player.level());
    }

    private static <T> DispatchKey<T, Item> dispatchByItem(Function<T, ItemStack> toStack) {
        return DispatchKey.of(Item.class, toStack.andThen(ItemStack::getItem));
    }

    private static DispatchKey<InventoryChangedEventJS, Item> dispatchByInventoryItem() {
        return DispatchKey.of(Item.class, e -> e.getItem().getItem());
    }

    EventBusForgeBridge FORGE_BRIDGE = EventBusForgeBridge.create(NeoForge.EVENT_BUS)
            .bind(LOGGED_IN)
            .bind(LOGGED_OUT)
            .bind(CHAT)
            // PlayerTickEvent fires on both logical sides: the SERVER bus delivers only server instances
            // (client player tick runs on the Render thread, and entering the SERVER Context there is
            // rejected by Graal as multi-threaded access).
            .bind(TICK_POST, e -> !e.getEntity().level().isClientSide())
            .bind(TICK_PRE, e -> !e.getEntity().level().isClientSide())
            .bind(CLONED)
            .bind(RESPAWNED)
            .bind(CHANGED_DIMENSION)
            .bind(ADVANCEMENT)
            .bind(CONTAINER_OPENED)
            .bind(INVENTORY_OPENED)
            .bind(CONTAINER_CLOSED)
            .bind(INVENTORY_CLOSED)
            // EntityInteract fires on both logical sides: the SERVER bus delivers only server instances.
            .bind(ENTITY_INTERACT, e -> !e.getLevel().isClientSide())
            .bind(CRAFTED)
            .bind(SMELTED)
            .bind(DESTROYED, NeoForgeShieldDestruction::forwardNative);
}
//?}
