package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.utils.event.dispatch.DispatchKey;
import com.tkisor.nekojs.wrapper.event.block.BlockBreakEventJS;
import com.tkisor.nekojs.wrapper.event.block.BlockLeftClickedEventJS;
import com.tkisor.nekojs.wrapper.event.block.BlockPlaceEventJS;
import com.tkisor.nekojs.wrapper.event.block.BlockRightClickEventJS;

public interface BlockEvents {
    EventGroup GROUP = EventGroup.of("BlockEvents");

    EventBusJS<BlockBreakEventJS, String> BROKEN =
            GROUP.server("broken", BlockBreakEventJS.class, DispatchKey.string());
    EventBusJS<BlockRightClickEventJS, String> RIGHT_CLICKED =
            GROUP.server("rightClicked", BlockRightClickEventJS.class, DispatchKey.string());
    EventBusJS<BlockPlaceEventJS, String> PLACED =
            GROUP.server("placed", BlockPlaceEventJS.class, DispatchKey.string());
    EventBusJS<BlockLeftClickedEventJS, String> LEFT_CLICKED =
            GROUP.server("leftClicked", BlockLeftClickedEventJS.class, DispatchKey.string());
}