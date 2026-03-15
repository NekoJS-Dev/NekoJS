package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.api.EventGroup;
import com.tkisor.nekojs.api.event.TargetedEventHandler;
import com.tkisor.nekojs.wrapper.event.item.ItemRightClickEventJS;

public interface ItemEvents {
    EventGroup GROUP = EventGroup.of("ItemEvents");

    TargetedEventHandler<ItemRightClickEventJS> RIGHT_CLICKED =
            GROUP.targetedServer("rightClicked", () -> ItemRightClickEventJS.class);
}