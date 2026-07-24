package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.api.event.EventBusForgeBridge;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.api.event.DispatchKey;
import com.tkisor.nekojs.eventbus.EventBusFactory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.event.entity.player.PlayerDestroyItemEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.function.Function;

public interface ItemEvents {
    EventGroup GROUP = EventGroup.of("ItemEvents");

    EventBusJS<PlayerInteractEvent.RightClickItem, Item> RIGHT_CLICKED =
            GROUP.server("rightClicked", PlayerInteractEvent.RightClickItem.class, dispatchByItem(PlayerInteractEvent::getItemStack));
    EventBusJS<ItemTooltipEvent, Item> TOOLTIP =
            GROUP.client("tooltip", ItemTooltipEvent.class, dispatchByItem(ItemTooltipEvent::getItemStack));


    EventBusJS<ItemEntityPickupEvent.Pre, Item> CAN_PICK_UP =
            GROUP.server("canPickUp", ItemEntityPickupEvent.Pre.class, dispatchByPickupItem());
    EventBusJS<ItemEntityPickupEvent.Pre, Item> PICKED_UP_PRE =
            GROUP.server("pickedUpPre", ItemEntityPickupEvent.Pre.class, dispatchByPickupItem());
    EventBusJS<ItemEntityPickupEvent.Post, Item> PICKED_UP =
            GROUP.server("pickedUp", ItemEntityPickupEvent.Post.class, dispatchByPickupItem());
    EventBusJS<ItemTossEvent, Item> DROPPED =
            GROUP.server("dropped", ItemTossEvent.class, dispatchByItem(event -> event.getEntity().getItem()));
    EventBusJS<PlayerInteractEvent.EntityInteract, Item> ENTITY_INTERACTED =
            GROUP.server("entityInteracted", PlayerInteractEvent.EntityInteract.class, dispatchByItem(PlayerInteractEvent.EntityInteract::getItemStack));
    // 物品使用完成（吃完食物/用完盾弓等）。对标 KubeJS foodEaten，基于 LivingEntityUseItemEvent.Finish，
    // 脚本侧可用 event.item.isEdible() 等进一步过滤是否食物。
    EventBusJS<LivingEntityUseItemEvent.Finish, Item> FOOD_EATEN =
            GROUP.server("foodEaten", LivingEntityUseItemEvent.Finish.class, dispatchByItem(LivingEntityUseItemEvent::getItem));


    private static <T> DispatchKey<T, Item> dispatchByItem(Function<T, ItemStack> toStack) {
        return EventBusFactory.createDispatchKey(Item.class, toStack.andThen(ItemStack::getItem));
    }

    private static <T extends ItemEntityPickupEvent> DispatchKey<T, Item> dispatchByPickupItem() {
        return dispatchByItem(event -> event.getItemEntity().getItem());
    }

    EventBusForgeBridge FORGE_BRIDGE = EventBusForgeBridge.create(NeoForge.EVENT_BUS)
            .bind(RIGHT_CLICKED)
            .bind(TOOLTIP)
            .bind(CAN_PICK_UP)
            .bind(PICKED_UP_PRE)
            .bind(PICKED_UP)
            .bind(DROPPED)
            .bind(ENTITY_INTERACTED)
            .bind(FOOD_EATEN);
}
