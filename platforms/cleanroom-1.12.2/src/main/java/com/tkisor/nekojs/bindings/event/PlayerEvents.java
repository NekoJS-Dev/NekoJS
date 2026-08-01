package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.api.event.EventBusForgeBridge;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.api.event.DispatchKey;
import com.tkisor.nekojs.eventbus.EventBusFactory;
import com.tkisor.nekojs.wrapper.event.player.InventoryChangedEventJS;
import net.minecraft.item.Item;
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

    EventBusJS<PlayerLoggedInEvent, Void> LOGGED_IN =
            GROUP.server("loggedIn", PlayerLoggedInEvent.class);
    EventBusJS<PlayerLoggedOutEvent, Void> LOGGED_OUT =
            GROUP.server("loggedOut", PlayerLoggedOutEvent.class);
    EventBusJS<ServerChatEvent, Void> CHAT =
            GROUP.server("chat", ServerChatEvent.class);
    // 保留无过滤的 tick（兼容现有脚本），与 tickPre/tickPost 共存
    EventBusJS<TickEvent.PlayerTickEvent, Void> TICK =
            GROUP.server("tick", TickEvent.PlayerTickEvent.class);
    // 1.12.2 单一类带 phase：tickPre/tickPost 用 filter 拆分
    EventBusJS<TickEvent.PlayerTickEvent, Void> TICK_PRE =
            GROUP.server("tickPre", TickEvent.PlayerTickEvent.class);
    EventBusJS<TickEvent.PlayerTickEvent, Void> TICK_POST =
            GROUP.server("tickPost", TickEvent.PlayerTickEvent.class);
    EventBusJS<PlayerEvent.Clone, Void> CLONED =
            GROUP.server("cloned", PlayerEvent.Clone.class);
    EventBusJS<PlayerRespawnEvent, Void> RESPAWNED =
            GROUP.server("respawned", PlayerRespawnEvent.class);
    EventBusJS<PlayerChangedDimensionEvent, Void> CHANGED_DIMENSION =
            GROUP.server("changedDimension", PlayerChangedDimensionEvent.class);
    EventBusJS<PlayerContainerEvent.Open, Void> CONTAINER_OPENED =
            GROUP.server("containerOpened", PlayerContainerEvent.Open.class);
    EventBusJS<PlayerContainerEvent.Close, Void> CONTAINER_CLOSED =
            GROUP.server("containerClosed", PlayerContainerEvent.Close.class);
    // inventoryOpened/inventoryClosed：与 containerOpened/closed 同 Forge 类的别名，便于脚本跨版本
    EventBusJS<PlayerContainerEvent.Open, Void> INVENTORY_OPENED =
            GROUP.server("inventoryOpened", PlayerContainerEvent.Open.class);
    EventBusJS<PlayerContainerEvent.Close, Void> INVENTORY_CLOSED =
            GROUP.server("inventoryClosed", PlayerContainerEvent.Close.class);
    EventBusJS<PlayerInteractEvent.EntityInteract, Void> ENTITY_INTERACT =
            GROUP.server("entityInteract", PlayerInteractEvent.EntityInteract.class);
    // 1.12.2 的 AdvancementEvent 是单一类（无 Pre/Post）
    EventBusJS<AdvancementEvent, Void> ADVANCEMENT =
            GROUP.server("advancement", AdvancementEvent.class);
    EventBusJS<ItemCraftedEvent, Item> CRAFTED =
            GROUP.server("crafted", ItemCraftedEvent.class,
                    EventBusFactory.createDispatchKey(Item.class,
                            e -> e.crafting.getItem()));
    EventBusJS<ItemSmeltedEvent, Item> SMELTED =
            GROUP.server("smelted", ItemSmeltedEvent.class,
                    EventBusFactory.createDispatchKey(Item.class,
                            e -> e.smelting.getItem()));
    EventBusJS<PlayerDestroyItemEvent, Item> DESTROYED =
            GROUP.server("destroyed", PlayerDestroyItemEvent.class,
                    EventBusFactory.createDispatchKey(Item.class,
                            e -> e.getOriginal().getItem()));

    /** 玩家物品栏变化：按物品 id 分发（{@code PlayerEvents.inventoryChanged('minecraft:stone', ...)}）。 */
    EventBusJS<InventoryChangedEventJS, Item> INVENTORY_CHANGED =
            GROUP.server("inventoryChanged", InventoryChangedEventJS.class,
                    EventBusFactory.createDispatchKey(Item.class,
                            e -> e.getItem().getItem()));

    EventBusForgeBridge FORGE_BRIDGE = EventBusForgeBridge.create(MinecraftForge.EVENT_BUS)
            .bind(LOGGED_IN)
            .bind(LOGGED_OUT)
            .bind(CHAT)
            .bind(TICK)
            .bind(TICK_PRE, e -> e.phase == TickEvent.Phase.START)
            .bind(TICK_POST, e -> e.phase == TickEvent.Phase.END)
            .bind(CLONED)
            .bind(RESPAWNED)
            .bind(CHANGED_DIMENSION)
            .bind(CONTAINER_OPENED)
            .bind(CONTAINER_CLOSED)
            .bind(INVENTORY_OPENED)
            .bind(INVENTORY_CLOSED)
            .bind(ENTITY_INTERACT)
            .bind(ADVANCEMENT)
            .bind(CRAFTED)
            .bind(SMELTED)
            .bind(DESTROYED);
}
