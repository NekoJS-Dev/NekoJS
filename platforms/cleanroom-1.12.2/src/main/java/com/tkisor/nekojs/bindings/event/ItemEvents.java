package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.api.event.EventBusForgeBridge;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.api.event.DispatchKey;
import com.tkisor.nekojs.eventbus.EventBusFactory;
import com.tkisor.nekojs.wrapper.event.item.ItemEventJS;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.item.ItemExpireEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

public interface ItemEvents {
    EventGroup GROUP = EventGroup.of("ItemEvents");

    // 跨平台统一 wrapper：EventBusJS 声明为 ItemEventJS，dispatch key 从 wrapper.item 提取（item.getItem()）
    EventBusJS<ItemEventJS, Item> RIGHT_CLICKED =
            GROUP.server("rightClicked", ItemEventJS.class, dispatchByItem());
    EventBusJS<ItemEventJS, Item> ENTITY_INTERACTED =
            GROUP.server("entityInteracted", ItemEventJS.class, dispatchByItem());
    EventBusJS<ItemEventJS, Item> FOOD_EATEN =
            GROUP.server("foodEaten", ItemEventJS.class, dispatchByItem());
    EventBusJS<ItemEventJS, Item> DROPPED =
            GROUP.server("dropped", ItemEventJS.class, dispatchByItem());
    // 1.12.2 无独立 Post 事件，canPickUp/pickedUp 绑同一 EntityItemPickupEvent 作为别名
    EventBusJS<ItemEventJS, Item> CAN_PICK_UP =
            GROUP.server("canPickUp", ItemEventJS.class, dispatchByItem());
    EventBusJS<ItemEventJS, Item> PICKED_UP =
            GROUP.server("pickedUp", ItemEventJS.class, dispatchByItem());
    EventBusJS<ItemEventJS, Item> TOOLTIP =
            GROUP.server("tooltip", ItemEventJS.class, dispatchByItem());

    // Cleanroom 独有：物品过期消失，保持原生绑定（无跨平台 wrapper）
    EventBusJS<ItemExpireEvent, Item> EXPIRE =
            GROUP.server("expire", ItemExpireEvent.class,
                    EventBusFactory.createDispatchKey(Item.class,
                            e -> e.getEntityItem().getItem().getItem()));

    private static DispatchKey<ItemEventJS, Item> dispatchByItem() {
        return EventBusFactory.createDispatchKey(Item.class, e -> e.getItem().getItem());
    }

    // —— 1.12.2 Item 事件 → ItemEventJS transformer ——
    // 统一字段名：player(EntityPlayer)、item(ItemStack)、level(World, 从 getWorld()/getEntityWorld() 映射)、
    //             hand(EnumHand)、target、entityItem(EntityItem)、resultItem
    EventBusForgeBridge FORGE_BRIDGE = EventBusForgeBridge.create(MinecraftForge.EVENT_BUS)
            // 玩家右键物品：RightClickItem
            .bindTransformed(RIGHT_CLICKED, e ->
                    new ItemEventJS(e.getEntityPlayer(), e.getItemStack(), e.getWorld()).withHand(e.getHand()),
                    PlayerInteractEvent.RightClickItem.class)
            // 玩家右键实体：EntityInteract，附带 target
            .bindTransformed(ENTITY_INTERACTED, e ->
                    new ItemEventJS(e.getEntityPlayer(), e.getItemStack(), e.getWorld())
                            .withHand(e.getHand()).withTarget(e.getTarget()),
                    PlayerInteractEvent.EntityInteract.class)
            // 物品使用完成（吃完食物/用完盾弓等）。resultItem 为使用后剩余物（Finish.getResultStack()）
            .bindTransformed(FOOD_EATEN, e ->
                    new ItemEventJS(e.getEntity() instanceof EntityPlayer p ? p : null,
                            e.getItem(), e.getEntity().getEntityWorld()).withResultItem(e.getResultStack()),
                    LivingEntityUseItemEvent.Finish.class)
            // 玩家丢弃物品：ItemTossEvent，getEntityItem()→EntityItem
            .bindTransformed(DROPPED, e ->
                    new ItemEventJS(e.getPlayer(), e.getEntityItem().getItem(), e.getPlayer().getEntityWorld())
                            .withEntityItem(e.getEntityItem()),
                    ItemTossEvent.class)
            // 拾取事件：EntityItemPickupEvent（1.12.2 无 Post 变体）
            // canPickUp/pickedUp 绑同一 Forge 类作为别名
            .bindTransformed(CAN_PICK_UP, e ->
                    new ItemEventJS(e.getEntityPlayer(), e.getItem().getItem(), e.getEntityPlayer().getEntityWorld())
                            .withEntityItem(e.getItem()),
                    EntityItemPickupEvent.class)
            .bindTransformed(PICKED_UP, e ->
                    new ItemEventJS(e.getEntityPlayer(), e.getItem().getItem(), e.getEntityPlayer().getEntityWorld())
                            .withEntityItem(e.getItem()),
                    EntityItemPickupEvent.class)
            // 物品 tooltip
            .bindTransformed(TOOLTIP, e ->
                    new ItemEventJS(e.getEntityPlayer(), e.getItemStack(), e.getEntityPlayer().getEntityWorld()),
                    ItemTooltipEvent.class)
            // 原生绑定：Cleanroom 独有 expire
            .bind(EXPIRE);
}
