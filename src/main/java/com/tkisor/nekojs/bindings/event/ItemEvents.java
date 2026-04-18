package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.api.event.EventBusForgeBridge;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.utils.event.dispatch.DispatchKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.function.Function;

public interface ItemEvents {
    EventGroup GROUP = EventGroup.of("ItemEvents");

    EventBusJS<PlayerInteractEvent.RightClickItem, Item> RIGHT_CLICKED =
            GROUP.server("rightClicked", PlayerInteractEvent.RightClickItem.class, dispatchByItem(PlayerInteractEvent::getItemStack));
    EventBusJS<ItemTooltipEvent, Item> TOOLTIP =
            GROUP.client("tooltip", ItemTooltipEvent.class, dispatchByItem(ItemTooltipEvent::getItemStack));

    EventBusJS<PlayerEvent.ItemCraftedEvent, Item> CRAFTED =
            GROUP.server("crafted", PlayerEvent.ItemCraftedEvent.class, dispatchByItem(PlayerEvent.ItemCraftedEvent::getCrafting));


    EventBusJS<LivingEntityUseItemEvent.Start, Item> USE_START =
            GROUP.server("useStarted", LivingEntityUseItemEvent.Start.class, dispatchByItem(LivingEntityUseItemEvent::getItem));
    EventBusJS<LivingEntityUseItemEvent.Stop, Item> USE_STOP =
            GROUP.server("useStopped", LivingEntityUseItemEvent.Stop.class, dispatchByItem(LivingEntityUseItemEvent::getItem));
    EventBusJS<LivingEntityUseItemEvent.Finish, Item> USE_FINISHED =
            GROUP.server("useFinished", LivingEntityUseItemEvent.Finish.class, dispatchByItem(LivingEntityUseItemEvent::getItem));
    EventBusJS<LivingEntityUseItemEvent.Tick, Item> USE_TICK =
            GROUP.server("useTick", LivingEntityUseItemEvent.Tick.class, dispatchByItem(LivingEntityUseItemEvent::getItem));

    private static <T> DispatchKey<T, Item> dispatchByItem(Function<T, ItemStack> toStack) {
        return DispatchKey.of(Item.class, toStack.andThen(ItemStack::getItem));
    }

    EventBusForgeBridge FORGE_BRIDGE = EventBusForgeBridge.create(NeoForge.EVENT_BUS)
            .bind(RIGHT_CLICKED)
            .bind(TOOLTIP)
            .bind(CRAFTED)
            .bind(USE_START)
            .bind(USE_STOP)
            .bind(USE_FINISHED)
            .bind(USE_TICK);
}