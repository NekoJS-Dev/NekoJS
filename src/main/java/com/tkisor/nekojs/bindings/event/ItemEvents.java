package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.api.event.EventBusForgeBridge;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.utils.event.dispatch.DispatchKey;
import com.tkisor.nekojs.wrapper.event.item.*;
import com.tkisor.nekojs.wrapper.event.player.PlayerChatEventJS;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

public interface ItemEvents {
    EventGroup GROUP = EventGroup.of("ItemEvents");

    EventBusJS<ItemRightClickEventJS, String> RIGHT_CLICKED =
            GROUP.server("rightClicked", ItemRightClickEventJS.class, DispatchKey.string());
    EventBusJS<ItemTooltipEventJS, Void> TOOLTIP =
            GROUP.client("tooltip", ItemTooltipEventJS.class);
    EventBusJS<ItemCraftedEventJS, Void> CRAFTED =
            GROUP.server("crafted", ItemCraftedEventJS.class);
    EventBusJS<ItemConsumedEventJS, String> CONSUMED =
            GROUP.server("consumed", ItemConsumedEventJS.class, DispatchKey.string());

    EventBusForgeBridge FORGE_BRIDGE = EventBusForgeBridge.create(NeoForge.EVENT_BUS)
            .bindTransformed(RIGHT_CLICKED, ItemRightClickEventJS::new, PlayerInteractEvent.RightClickItem.class);
}