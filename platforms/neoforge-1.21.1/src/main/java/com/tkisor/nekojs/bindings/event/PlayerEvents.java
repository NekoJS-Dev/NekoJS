package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.api.event.EventBusForgeBridge;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.api.event.DispatchKey;
import com.tkisor.nekojs.wrapper.event.player.InventoryChangedEventJS;
import com.tkisor.nekojs.wrapper.event.player.PlayerEventJS;
import com.tkisor.nekojs.eventbus.EventBusFactory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.player.*;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.function.Function;

public interface PlayerEvents {
    EventGroup GROUP = EventGroup.of("PlayerEvents");

    // 跨平台统一 wrapper：EventBusJS 声明为 PlayerEventJS，dispatch key 从 wrapper 提取
    EventBusJS<PlayerEventJS, Void> LOGGED_IN =
            GROUP.server("loggedIn", PlayerEventJS.class);
    EventBusJS<PlayerEventJS, Void> LOGGED_OUT =
            GROUP.server("loggedOut", PlayerEventJS.class);
    EventBusJS<PlayerEventJS, Void> CHAT =
            GROUP.server("chat", PlayerEventJS.class, true);
    EventBusJS<PlayerEventJS, Void> TICK_POST =
            GROUP.server("tickPost", PlayerEventJS.class);
    EventBusJS<PlayerEventJS, Void> TICK_PRE =
            GROUP.server("tickPre", PlayerEventJS.class);
    EventBusJS<PlayerEventJS, Void> CLONED =
            GROUP.server("cloned", PlayerEventJS.class);
    EventBusJS<PlayerEventJS, Void> RESPAWNED =
            GROUP.server("respawned", PlayerEventJS.class);
    EventBusJS<PlayerEventJS, Void> CHANGED_DIMENSION =
            GROUP.server("changedDimension", PlayerEventJS.class);
    EventBusJS<PlayerEventJS, Void> ADVANCEMENT =
            GROUP.server("advancement", PlayerEventJS.class);
    EventBusJS<PlayerEventJS, Void> CONTAINER_OPENED =
            GROUP.server("containerOpened", PlayerEventJS.class);
    EventBusJS<PlayerEventJS, Void> INVENTORY_OPENED =
            GROUP.server("inventoryOpened", PlayerEventJS.class);
    EventBusJS<PlayerEventJS, Void> CONTAINER_CLOSED =
            GROUP.server("containerClosed", PlayerEventJS.class);
    EventBusJS<PlayerEventJS, Void> INVENTORY_CLOSED =
            GROUP.server("inventoryClosed", PlayerEventJS.class);
    EventBusJS<PlayerEventJS, Void> ENTITY_INTERACT =
            GROUP.server("entityInteract", PlayerEventJS.class, true);
    // crafted/smelted/destroyed 按 Item 分发
    EventBusJS<PlayerEventJS, Item> CRAFTED =
            GROUP.server("crafted", PlayerEventJS.class, dispatchByItem(PlayerEventJS::getCrafting));
    EventBusJS<PlayerEventJS, Item> SMELTED =
            GROUP.server("smelted", PlayerEventJS.class, dispatchByItem(PlayerEventJS::getSmelting));
    EventBusJS<PlayerEventJS, Item> DESTROYED =
            GROUP.server("destroyed", PlayerEventJS.class, dispatchByItem(PlayerEventJS::getDestroyedItem));

    /** 玩家物品栏变化：按物品 id 分发（{@code PlayerEvents.inventoryChanged('minecraft:stone', ...)}）。 */
    EventBusJS<InventoryChangedEventJS, Item> INVENTORY_CHANGED =
            GROUP.server("inventoryChanged", InventoryChangedEventJS.class, dispatchByInventoryItem());

    private static <T> DispatchKey<T, Item> dispatchByItem(Function<T, ItemStack> toStack) {
        return EventBusFactory.createDispatchKey(Item.class, toStack.andThen(ItemStack::getItem));
    }

    private static DispatchKey<InventoryChangedEventJS, Item> dispatchByInventoryItem() {
        return EventBusFactory.createDispatchKey(Item.class, e -> e.getItem().getItem());
    }

    // —— NeoForge PlayerEvent/相关 → PlayerEventJS transformer ——
    // 统一字段名：player（来自 getEntity()），message/from/to/container/advancement/original/wasDeath
    private static PlayerEventJS fromPlayerEvent(PlayerEvent event) {
        return new PlayerEventJS(event.getEntity());
    }

    // ServerChatEvent 不继承 PlayerEvent，单独转换；player 来自 getPlayer()
    private static PlayerEventJS fromChat(ServerChatEvent event) {
        return new PlayerEventJS(event.getPlayer()).withMessage(event.getRawText());
    }

    EventBusForgeBridge FORGE_BRIDGE = EventBusForgeBridge.create(NeoForge.EVENT_BUS)
            .bindTransformed(LOGGED_IN, PlayerEvents::fromPlayerEvent, PlayerEvent.PlayerLoggedInEvent.class)
            .bindTransformed(LOGGED_OUT, PlayerEvents::fromPlayerEvent, PlayerEvent.PlayerLoggedOutEvent.class)
            .bindTransformed(CHAT, PlayerEvents::fromChat, ServerChatEvent.class)
            .bindTransformed(TICK_POST, PlayerEvents::fromPlayerEvent, PlayerTickEvent.Post.class)
            .bindTransformed(TICK_PRE, PlayerEvents::fromPlayerEvent, PlayerTickEvent.Pre.class)
            .bindTransformed(CLONED, e ->
                    fromPlayerEvent(e).withOriginal(e.getOriginal()).withWasDeath(e.isWasDeath()),
                    PlayerEvent.Clone.class)
            .bindTransformed(RESPAWNED, PlayerEvents::fromPlayerEvent, PlayerEvent.PlayerRespawnEvent.class)
            .bindTransformed(CHANGED_DIMENSION, e ->
                    fromPlayerEvent(e).withDimension(
                            e.getFrom().location().toString(),
                            e.getTo().location().toString()),
                    PlayerEvent.PlayerChangedDimensionEvent.class)
            .bindTransformed(ADVANCEMENT, e ->
                    fromPlayerEvent(e).withAdvancement(e.getAdvancement()),
                    AdvancementEvent.AdvancementEarnEvent.class)
            .bindTransformed(CONTAINER_OPENED, e ->
                    fromPlayerEvent(e).withContainer(e.getContainer()), PlayerContainerEvent.Open.class)
            .bindTransformed(INVENTORY_OPENED, e ->
                    fromPlayerEvent(e).withContainer(e.getContainer()), PlayerContainerEvent.Open.class)
            .bindTransformed(CONTAINER_CLOSED, e ->
                    fromPlayerEvent(e).withContainer(e.getContainer()), PlayerContainerEvent.Close.class)
            .bindTransformed(INVENTORY_CLOSED, e ->
                    fromPlayerEvent(e).withContainer(e.getContainer()), PlayerContainerEvent.Close.class)
            .bindTransformed(ENTITY_INTERACT, e ->
                    fromPlayerEvent(e).withTarget(e.getTarget()), PlayerInteractEvent.EntityInteract.class)
            .bindTransformed(CRAFTED, e ->
                    fromPlayerEvent(e).withCrafting(e.getCrafting()), PlayerEvent.ItemCraftedEvent.class)
            .bindTransformed(SMELTED, e ->
                    fromPlayerEvent(e).withSmelting(e.getSmelting()), PlayerEvent.ItemSmeltedEvent.class)
            .bindTransformed(DESTROYED, e ->
                    fromPlayerEvent(e).withOriginal(e.getOriginal()), PlayerDestroyItemEvent.class);
}
