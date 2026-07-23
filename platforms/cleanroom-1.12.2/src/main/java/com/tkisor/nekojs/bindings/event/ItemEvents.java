package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.api.event.EventBusForgeBridge;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.api.event.DispatchKey;
import com.tkisor.nekojs.eventbus.EventBusFactory;
import net.minecraft.item.Item;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.item.ItemExpireEvent;

public interface ItemEvents {
    EventGroup GROUP = EventGroup.of("ItemEvents");

    EventBusJS<ItemTooltipEvent, Item> TOOLTIP =
            GROUP.server("tooltip", ItemTooltipEvent.class,
                    EventBusFactory.createDispatchKey(Item.class,
                            e -> e.getItemStack().getItem()));
    EventBusJS<ItemTossEvent, Item> TOSS =
            GROUP.server("toss", ItemTossEvent.class,
                    EventBusFactory.createDispatchKey(Item.class,
                            e -> e.getEntityItem().getItem().getItem()));
    EventBusJS<ItemExpireEvent, Item> EXPIRE =
            GROUP.server("expire", ItemExpireEvent.class,
                    EventBusFactory.createDispatchKey(Item.class,
                            e -> e.getEntityItem().getItem().getItem()));
    // 玩家右键物品：PlayerInteractEvent.RightClickItem，key 取 e.getItemStack().getItem()
    EventBusJS<PlayerInteractEvent.RightClickItem, Item> RIGHT_CLICKED =
            GROUP.server("rightClicked", PlayerInteractEvent.RightClickItem.class,
                    EventBusFactory.createDispatchKey(Item.class,
                            e -> e.getItemStack().getItem()));
    // 拾取事件：1.12.2 类名为 EntityItemPickupEvent（无 Post 变体）。
    // getItem()→EntityItem，再 .getItem()→ItemStack，再 .getItem()→Item
    // canPickUp/pickedUp 绑同一 Forge 类作为别名（1.12.2 无独立的 Post 事件）
    EventBusJS<EntityItemPickupEvent, Item> CAN_PICK_UP =
            GROUP.server("canPickUp", EntityItemPickupEvent.class,
                    EventBusFactory.createDispatchKey(Item.class,
                            e -> e.getItem().getItem().getItem()));
    EventBusJS<EntityItemPickupEvent, Item> PICKED_UP =
            GROUP.server("pickedUp", EntityItemPickupEvent.class,
                    EventBusFactory.createDispatchKey(Item.class,
                            e -> e.getItem().getItem().getItem()));
    // 玩家右键实体：PlayerInteractEvent.EntityInteract，key 取 e.getItemStack().getItem()
    EventBusJS<PlayerInteractEvent.EntityInteract, Item> ENTITY_INTERACTED =
            GROUP.server("entityInteracted", PlayerInteractEvent.EntityInteract.class,
                    EventBusFactory.createDispatchKey(Item.class,
                            e -> e.getItemStack().getItem()));

    EventBusForgeBridge FORGE_BRIDGE = EventBusForgeBridge.create(MinecraftForge.EVENT_BUS)
            .bind(TOOLTIP)
            .bind(TOSS)
            .bind(EXPIRE)
            .bind(RIGHT_CLICKED)
            .bind(CAN_PICK_UP)
            .bind(PICKED_UP)
            .bind(ENTITY_INTERACTED);
}
