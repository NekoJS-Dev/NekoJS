package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.api.event.EventBusForgeBridge;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.api.event.DispatchKey;
import com.tkisor.nekojs.eventbus.EventBusFactory;
import com.tkisor.nekojs.wrapper.event.player.InventoryChangedEventJS;
import com.tkisor.nekojs.wrapper.event.player.PlayerEventJS;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.entity.player.AdvancementEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.entity.player.PlayerDestroyItemEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerRespawnEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerChangedDimensionEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.ItemCraftedEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.ItemSmeltedEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public interface PlayerEvents {
    EventGroup GROUP = EventGroup.of("PlayerEvents");

    // 跨平台统一 wrapper：EventBusJS 声明为 PlayerEventJS，dispatch key 从 wrapper 提取
    EventBusJS<PlayerEventJS, Void> LOGGED_IN =
            GROUP.server("loggedIn", PlayerEventJS.class);
    EventBusJS<PlayerEventJS, Void> LOGGED_OUT =
            GROUP.server("loggedOut", PlayerEventJS.class);
    EventBusJS<PlayerEventJS, Void> CHAT =
            GROUP.server("chat", PlayerEventJS.class);
    // tick 事件：1.12.2 单一类带 phase，cleanroom bridge 的 bind(bus, filter) 不支持 transformed，
    // 因此 tickPre/tickPost/tick 保持原生 TickEvent.PlayerTickEvent（见 FORGE_BRIDGE 注释）
    EventBusJS<TickEvent.PlayerTickEvent, Void> TICK =
            GROUP.server("tick", TickEvent.PlayerTickEvent.class);
    EventBusJS<TickEvent.PlayerTickEvent, Void> TICK_PRE =
            GROUP.server("tickPre", TickEvent.PlayerTickEvent.class);
    EventBusJS<TickEvent.PlayerTickEvent, Void> TICK_POST =
            GROUP.server("tickPost", TickEvent.PlayerTickEvent.class);
    EventBusJS<PlayerEventJS, Void> CLONED =
            GROUP.server("cloned", PlayerEventJS.class);
    EventBusJS<PlayerEventJS, Void> RESPAWNED =
            GROUP.server("respawned", PlayerEventJS.class);
    EventBusJS<PlayerEventJS, Void> CHANGED_DIMENSION =
            GROUP.server("changedDimension", PlayerEventJS.class);
    EventBusJS<PlayerEventJS, Void> CONTAINER_OPENED =
            GROUP.server("containerOpened", PlayerEventJS.class);
    EventBusJS<PlayerEventJS, Void> CONTAINER_CLOSED =
            GROUP.server("containerClosed", PlayerEventJS.class);
    EventBusJS<PlayerEventJS, Void> INVENTORY_OPENED =
            GROUP.server("inventoryOpened", PlayerEventJS.class);
    EventBusJS<PlayerEventJS, Void> INVENTORY_CLOSED =
            GROUP.server("inventoryClosed", PlayerEventJS.class);
    EventBusJS<PlayerEventJS, Void> ENTITY_INTERACT =
            GROUP.server("entityInteract", PlayerEventJS.class);
    // 1.12.2 的 AdvancementEvent 是单一类（无 Pre/Post）
    EventBusJS<PlayerEventJS, Void> ADVANCEMENT =
            GROUP.server("advancement", PlayerEventJS.class);
    // crafted/smelted/destroyed 按 Item 分发
    EventBusJS<PlayerEventJS, Item> CRAFTED =
            GROUP.server("crafted", PlayerEventJS.class, dispatchByItem(PlayerEventJS::getCrafting));
    EventBusJS<PlayerEventJS, Item> SMELTED =
            GROUP.server("smelted", PlayerEventJS.class, dispatchByItem(PlayerEventJS::getSmelting));
    EventBusJS<PlayerEventJS, Item> DESTROYED =
            GROUP.server("destroyed", PlayerEventJS.class, dispatchByItem(PlayerEventJS::getDestroyedItem));

    /** 玩家物品栏变化：按物品 id 分发（{@code PlayerEvents.inventoryChanged('minecraft:stone', ...)}）。 */
    EventBusJS<InventoryChangedEventJS, Item> INVENTORY_CHANGED =
            GROUP.server("inventoryChanged", InventoryChangedEventJS.class,
                    EventBusFactory.createDispatchKey(Item.class,
                            e -> e.getItem().getItem()));

    private static <T> DispatchKey<T, Item> dispatchByItem(java.util.function.Function<T, ItemStack> toStack) {
        return EventBusFactory.createDispatchKey(Item.class, toStack.andThen(ItemStack::getItem));
    }

    // —— 1.12.2 PlayerEvent/相关 → PlayerEventJS transformer ——
    // 统一字段名：player。fml.gameevent.PlayerEvent 用 public 字段 player；
    // entity.player.PlayerEvent 用 getEntityPlayer()。
    private static PlayerEventJS fromFmlPlayer(
            net.minecraftforge.fml.common.gameevent.PlayerEvent event) {
        return new PlayerEventJS(event.player);
    }

    private static PlayerEventJS fromEntityPlayer(PlayerEvent event) {
        return new PlayerEventJS(event.getEntityPlayer());
    }

    // ServerChatEvent 不继承 PlayerEvent，player 来自 getPlayer()
    private static PlayerEventJS fromChat(ServerChatEvent event) {
        return new PlayerEventJS(event.getPlayer()).withMessage(event.getMessage());
    }

    EventBusForgeBridge FORGE_BRIDGE = EventBusForgeBridge.create(MinecraftForge.EVENT_BUS)
            .bindTransformed(LOGGED_IN, PlayerEvents::fromFmlPlayer, PlayerLoggedInEvent.class)
            .bindTransformed(LOGGED_OUT, PlayerEvents::fromFmlPlayer, PlayerLoggedOutEvent.class)
            .bindTransformed(CHAT, PlayerEvents::fromChat, ServerChatEvent.class)
            // tick 保持原生 + filter：transformed-bind-with-filter 不存在于 cleanroom bridge
            .bind(TICK)
            .bind(TICK_PRE, e -> e.phase == TickEvent.Phase.START)
            .bind(TICK_POST, e -> e.phase == TickEvent.Phase.END)
            .bindTransformed(CLONED, e ->
                    fromEntityPlayer(e).withOriginal(e.getOriginal()).withWasDeath(e.isWasDeath()),
                    PlayerEvent.Clone.class)
            .bindTransformed(RESPAWNED, PlayerEvents::fromFmlPlayer, PlayerRespawnEvent.class)
            .bindTransformed(CHANGED_DIMENSION, e ->
                    fromFmlPlayer(e).withDimension(
                            String.valueOf(e.fromDim),
                            String.valueOf(e.toDim)),
                    PlayerChangedDimensionEvent.class)
            .bindTransformed(CONTAINER_OPENED, e ->
                    fromEntityPlayer(e).withContainer(e.getContainer()), PlayerContainerEvent.Open.class)
            .bindTransformed(CONTAINER_CLOSED, e ->
                    fromEntityPlayer(e).withContainer(e.getContainer()), PlayerContainerEvent.Close.class)
            .bindTransformed(INVENTORY_OPENED, e ->
                    fromEntityPlayer(e).withContainer(e.getContainer()), PlayerContainerEvent.Open.class)
            .bindTransformed(INVENTORY_CLOSED, e ->
                    fromEntityPlayer(e).withContainer(e.getContainer()), PlayerContainerEvent.Close.class)
            .bindTransformed(ENTITY_INTERACT, e ->
                    fromEntityPlayer(e).withTarget(e.getTarget()), PlayerInteractEvent.EntityInteract.class)
            .bindTransformed(ADVANCEMENT, e ->
                    fromEntityPlayer(e).withAdvancement(e.getAdvancement()), AdvancementEvent.class)
            .bindTransformed(CRAFTED, e ->
                    fromFmlPlayer(e).withCrafting(e.crafting), ItemCraftedEvent.class)
            .bindTransformed(SMELTED, e ->
                    fromFmlPlayer(e).withSmelting(e.smelting), ItemSmeltedEvent.class)
            .bindTransformed(DESTROYED, e ->
                    fromEntityPlayer(e).withOriginal(e.getOriginal()), PlayerDestroyItemEvent.class);
}
