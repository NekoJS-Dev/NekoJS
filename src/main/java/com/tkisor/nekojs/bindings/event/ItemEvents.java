package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.utils.event.dispatch.DispatchKey;
import com.tkisor.nekojs.wrapper.event.item.ItemCraftedEventJS;
import com.tkisor.nekojs.wrapper.event.item.ItemRightClickEventJS;
import com.tkisor.nekojs.wrapper.event.item.ItemTooltipEventJS;

public interface ItemEvents {
    EventGroup GROUP = EventGroup.of("ItemEvents");

    EventBusJS<ItemRightClickEventJS, String> RIGHT_CLICKED =
            GROUP.server("rightClicked", ItemRightClickEventJS.class, DispatchKey.string());
    EventBusJS<ItemTooltipEventJS, Void> TOOLTIP =
            GROUP.client("tooltip", ItemTooltipEventJS.class);
    EventBusJS<ItemCraftedEventJS, Void> CRAFTED =
            GROUP.server("crafted", ItemCraftedEventJS.class);
}