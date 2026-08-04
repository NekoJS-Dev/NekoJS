package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.api.event.EventBusForgeBridge;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.api.event.DispatchKey;
import com.tkisor.nekojs.eventbus.EventBusFactory;
import com.tkisor.nekojs.wrapper.event.item.ItemEventJS;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

public interface ItemEvents {
    EventGroup GROUP = EventGroup.of("ItemEvents");

    // 跨平台统一 wrapper：EventBusJS 声明为 ItemEventJS，dispatch key 从 wrapper.item 提取（item.getItem()）
    // cancellable 事件显式传 true（rightClicked/entityInteracted/dropped/canPickUp/pickedUpPre）
    EventBusJS<ItemEventJS, Item> RIGHT_CLICKED =
            GROUP.server("rightClicked", ItemEventJS.class, true, dispatchByItem());
    EventBusJS<ItemEventJS, Item> ENTITY_INTERACTED =
            GROUP.server("entityInteracted", ItemEventJS.class, true, dispatchByItem());
    EventBusJS<ItemEventJS, Item> FOOD_EATEN =
            GROUP.server("foodEaten", ItemEventJS.class, dispatchByItem());
    EventBusJS<ItemEventJS, Item> DROPPED =
            GROUP.server("dropped", ItemEventJS.class, true, dispatchByItem());
    EventBusJS<ItemEventJS, Item> CAN_PICK_UP =
            GROUP.server("canPickUp", ItemEventJS.class, true, dispatchByItem());
    // pickedUpPre 为 NeoForge 独有（ItemEntityPickupEvent.Pre），跨平台无对应 wrapper 别名
    EventBusJS<ItemEventJS, Item> PICKED_UP_PRE =
            GROUP.server("pickedUpPre", ItemEventJS.class, true, dispatchByItem());
    EventBusJS<ItemEventJS, Item> PICKED_UP =
            GROUP.server("pickedUp", ItemEventJS.class, dispatchByItem());
    EventBusJS<ItemEventJS, Item> TOOLTIP =
            GROUP.client("tooltip", ItemEventJS.class, dispatchByItem());

    private static DispatchKey<ItemEventJS, Item> dispatchByItem() {
        return EventBusFactory.createDispatchKey(Item.class, e -> e.getItem().getItem());
    }

    // —— NeoForge Item 事件 → ItemEventJS transformer ——
    // 统一字段名：player、item(ItemStack)、level、hand、target、entityItem、resultItem
    EventBusForgeBridge FORGE_BRIDGE = EventBusForgeBridge.create(NeoForge.EVENT_BUS)
            // 玩家右键物品：RightClickItem，item 取 getItemStack()，hand 取 getHand()
            .bindTransformed(RIGHT_CLICKED, e ->
                    new ItemEventJS(e.getEntity(), e.getItemStack(), e.getLevel()).withHand(e.getHand()),
                    PlayerInteractEvent.RightClickItem.class)
            // 玩家右键实体：EntityInteract，附带 target
            .bindTransformed(ENTITY_INTERACTED, e ->
                    new ItemEventJS(e.getEntity(), e.getItemStack(), e.getLevel())
                            .withHand(e.getHand()).withTarget(e.getTarget()),
                    PlayerInteractEvent.EntityInteract.class)
            // 物品使用完成（吃完食物/用完盾弓等）。resultItem 为使用后剩余物（Finish.getResultStack()）
            .bindTransformed(FOOD_EATEN, e ->
                    new ItemEventJS(e.getEntity() instanceof net.minecraft.world.entity.player.Player p ? p : null,
                            e.getItem(), e.getEntity().level()).withResultItem(e.getResultStack()),
                    LivingEntityUseItemEvent.Finish.class)
            // 玩家丢弃物品：ItemTossEvent，entity 为 ItemEntity，item 取其内部 ItemStack
            .bindTransformed(DROPPED, e ->
                    new ItemEventJS(e.getPlayer(), e.getEntity().getItem(), e.getEntity().level())
                            .withEntityItem(e.getEntity()),
                    ItemTossEvent.class)
            // 拾取前置：ItemEntityPickupEvent.Pre，可决定是否允许拾取
            .bindTransformed(CAN_PICK_UP, e ->
                    new ItemEventJS(e.getPlayer(), e.getItemEntity().getItem(), e.getItemEntity().level())
                            .withEntityItem(e.getItemEntity()),
                    ItemEntityPickupEvent.Pre.class)
            // pickedUpPre 复用 Pre 事件（NeoForge 独有别名）
            .bindTransformed(PICKED_UP_PRE, e ->
                    new ItemEventJS(e.getPlayer(), e.getItemEntity().getItem(), e.getItemEntity().level())
                            .withEntityItem(e.getItemEntity()),
                    ItemEntityPickupEvent.Pre.class)
            // 拾取完成：ItemEntityPickupEvent.Post
            .bindTransformed(PICKED_UP, e ->
                    new ItemEventJS(e.getPlayer(), e.getItemEntity().getItem(), e.getItemEntity().level())
                            .withEntityItem(e.getItemEntity()),
                    ItemEntityPickupEvent.Post.class)
            // 物品 tooltip：客户端事件
            .bindTransformed(TOOLTIP, e ->
                    new ItemEventJS(e.getEntity(), e.getItemStack(), null),
                    ItemTooltipEvent.class);
}
