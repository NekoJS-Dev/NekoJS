package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.api.event.EventBusForgeBridge;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.utils.event.dispatch.DispatchKey;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

public interface ItemEvents {
    EventGroup GROUP = EventGroup.of("ItemEvents");

    EventBusJS<PlayerInteractEvent.RightClickItem, String> RIGHT_CLICKED =
            GROUP.server("rightClicked", PlayerInteractEvent.RightClickItem.class, DispatchKey.string());
    EventBusJS<ItemTooltipEvent, Void> TOOLTIP =
            GROUP.client("tooltip", ItemTooltipEvent.class);
    EventBusJS<PlayerEvent.ItemCraftedEvent, Void> CRAFTED =
            GROUP.server("crafted", PlayerEvent.ItemCraftedEvent.class);
    EventBusJS<LivingEntityUseItemEvent.Finish, String> CONSUMED =
            GROUP.server("consumed", LivingEntityUseItemEvent.Finish.class, DispatchKey.string());

    EventBusForgeBridge FORGE_BRIDGE = EventBusForgeBridge.create(NeoForge.EVENT_BUS)
            .bind(RIGHT_CLICKED)
            .bind(TOOLTIP)
            .bind(CRAFTED)
            .bind(CONSUMED);
}