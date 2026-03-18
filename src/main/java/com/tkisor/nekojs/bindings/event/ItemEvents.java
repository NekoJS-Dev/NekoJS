package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.api.data.EventGroup;
import com.tkisor.nekojs.api.event.EventHandler;
import com.tkisor.nekojs.api.event.TargetedEventHandler;
import com.tkisor.nekojs.wrapper.event.item.ItemCraftedEventJS;
import com.tkisor.nekojs.wrapper.event.item.ItemRightClickEventJS;
import com.tkisor.nekojs.wrapper.event.item.ItemTooltipEventJS;

public interface ItemEvents {
    EventGroup GROUP = EventGroup.of("ItemEvents");

    TargetedEventHandler<ItemRightClickEventJS> RIGHT_CLICKED =
            GROUP.targetedServer("rightClicked", () -> ItemRightClickEventJS.class);
    EventHandler<ItemTooltipEventJS> TOOLTIP =
            GROUP.client("tooltip", () -> ItemTooltipEventJS.class);
    EventHandler<ItemCraftedEventJS> CRAFTED =
            GROUP.server("crafted", () -> ItemCraftedEventJS.class);
}