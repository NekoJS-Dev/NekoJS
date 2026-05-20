package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.api.event.EventBusForgeBridge;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.utils.event.dispatch.DispatchKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.player.*;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.function.Function;

public interface PlayerEvents {
    EventGroup GROUP = EventGroup.of("PlayerEvents");

    EventBusJS<PlayerEvent.PlayerLoggedInEvent, Void> LOGGED_IN =
            GROUP.server("loggedIn", PlayerEvent.PlayerLoggedInEvent.class);
    EventBusJS<PlayerEvent.PlayerLoggedOutEvent, Void> LOGGED_OUT =
            GROUP.server("loggedOut", PlayerEvent.PlayerLoggedOutEvent.class);
    EventBusJS<ServerChatEvent, Void> CHAT =
            GROUP.server("chat", ServerChatEvent.class);
    EventBusJS<PlayerTickEvent.Post, Void> TICK_POST =
            GROUP.server("tickPost", PlayerTickEvent.Post.class);
    EventBusJS<PlayerTickEvent.Pre, Void> TICK_PRE =
            GROUP.server("tickPre", PlayerTickEvent.Pre.class);
    EventBusJS<PlayerEvent.Clone, Void> CLONED =
            GROUP.server("cloned", PlayerEvent.Clone.class);
    EventBusJS<PlayerEvent.PlayerRespawnEvent, Void> RESPAWNED =
            GROUP.server("respawned", PlayerEvent.PlayerRespawnEvent.class);
    EventBusJS<PlayerEvent.PlayerChangedDimensionEvent, Void> CHANGED_DIMENSION =
            GROUP.server("changedDimension", PlayerEvent.PlayerChangedDimensionEvent.class);
    EventBusJS<AdvancementEvent.AdvancementEarnEvent, Void> ADVANCEMENT =
            GROUP.server("advancement", AdvancementEvent.AdvancementEarnEvent.class);
    EventBusJS<PlayerContainerEvent.Open, Void> CONTAINER_OPENED =
            GROUP.server("containerOpened", PlayerContainerEvent.Open.class);
    EventBusJS<PlayerContainerEvent.Open, Void> INVENTORY_OPENED =
            GROUP.server("inventoryOpened", PlayerContainerEvent.Open.class);
    EventBusJS<PlayerContainerEvent.Close, Void> CONTAINER_CLOSED =
            GROUP.server("containerClosed", PlayerContainerEvent.Close.class);
    EventBusJS<PlayerContainerEvent.Close, Void> INVENTORY_CLOSED =
            GROUP.server("inventoryClosed", PlayerContainerEvent.Close.class);
    EventBusJS<PlayerInteractEvent.EntityInteract, Void> ENTITY_INTERACT =
            GROUP.server("entityInteract", PlayerInteractEvent.EntityInteract.class);
    EventBusJS<PlayerEvent.ItemCraftedEvent, Item> CRAFTED =
            GROUP.server("crafted", PlayerEvent.ItemCraftedEvent.class, dispatchByItem(PlayerEvent.ItemCraftedEvent::getCrafting));
    EventBusJS<PlayerEvent.ItemSmeltedEvent, Item> SMELTED =
            GROUP.server("smelted", PlayerEvent.ItemSmeltedEvent.class, dispatchByItem(PlayerEvent.ItemSmeltedEvent::getSmelting));
    EventBusJS<PlayerDestroyItemEvent, Item> DESTROYED =
            GROUP.server("destroyed", PlayerDestroyItemEvent.class, dispatchByItem(PlayerDestroyItemEvent::getOriginal));

    private static <T> DispatchKey<T, Item> dispatchByItem(Function<T, ItemStack> toStack) {
        return DispatchKey.of(Item.class, toStack.andThen(ItemStack::getItem));
    }

    private static <T extends ItemEntityPickupEvent> DispatchKey<T, Item> dispatchByPickupItem() {
        return dispatchByItem(event -> event.getItemEntity().getItem());
    }

    EventBusForgeBridge FORGE_BRIDGE = EventBusForgeBridge.create(NeoForge.EVENT_BUS)
            .bind(LOGGED_IN)
            .bind(LOGGED_OUT)
            .bind(CHAT)
            .bind(TICK_POST)
            .bind(TICK_PRE)
            .bind(CLONED)
            .bind(RESPAWNED)
            .bind(CHANGED_DIMENSION)
            .bind(ADVANCEMENT)
            .bind(CONTAINER_OPENED)
            .bind(INVENTORY_OPENED)
            .bind(CONTAINER_CLOSED)
            .bind(INVENTORY_CLOSED)
            .bind(ENTITY_INTERACT)
            .bind(CRAFTED)
            .bind(SMELTED)
            .bind(DESTROYED);
}
