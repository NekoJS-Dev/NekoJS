package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.api.event.EventBusForgeBridge;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.utils.event.dispatch.DispatchKey;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.function.Function;

public interface ItemEvents {
    EventGroup GROUP = EventGroup.of("ItemEvents");

    EventBusJS<PlayerInteractEvent.RightClickItem, ItemStack> RIGHT_CLICKED =
            GROUP.server("rightClicked", PlayerInteractEvent.RightClickItem.class, dispatchByStack(PlayerInteractEvent::getItemStack));
    EventBusJS<ItemTooltipEvent, ItemStack> TOOLTIP =
            GROUP.client("tooltip", ItemTooltipEvent.class, dispatchByStack(ItemTooltipEvent::getItemStack));

    EventBusJS<PlayerEvent.ItemCraftedEvent, ItemStack> CRAFTED =
            GROUP.server("crafted", PlayerEvent.ItemCraftedEvent.class, dispatchByStack(PlayerEvent.ItemCraftedEvent::getCrafting));


    EventBusJS<LivingEntityUseItemEvent.Start, ItemStack> USE_START =
            GROUP.server("useStarted", LivingEntityUseItemEvent.Start.class, dispatchByStack(LivingEntityUseItemEvent::getItem));
    EventBusJS<LivingEntityUseItemEvent.Stop, ItemStack> USE_STOP =
            GROUP.server("useStopped", LivingEntityUseItemEvent.Stop.class, dispatchByStack(LivingEntityUseItemEvent::getItem));
    EventBusJS<LivingEntityUseItemEvent.Finish, ItemStack> USE_FINISHED =
            GROUP.server("useFinished", LivingEntityUseItemEvent.Finish.class, dispatchByStack(LivingEntityUseItemEvent::getItem));
    EventBusJS<LivingEntityUseItemEvent.Tick, ItemStack> USE_TICK =
            GROUP.server("useTick", LivingEntityUseItemEvent.Tick.class, dispatchByStack(LivingEntityUseItemEvent::getItem));

    private static <T> DispatchKey<T, ItemStack> dispatchByStack(Function<T, ItemStack> toKey) {
        return DispatchKey.of(ItemStack.class, toKey);
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