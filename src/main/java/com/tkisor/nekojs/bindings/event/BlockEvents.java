package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.api.data.EventGroup;
import com.tkisor.nekojs.api.event.EventHandler;
import com.tkisor.nekojs.api.event.TargetedEventHandler;
import com.tkisor.nekojs.wrapper.event.block.BlockBreakEventJS;
import com.tkisor.nekojs.wrapper.event.block.BlockLeftClickedEventJS;
import com.tkisor.nekojs.wrapper.event.block.BlockPlaceEventJS;
import com.tkisor.nekojs.wrapper.event.block.BlockRightClickEventJS;

public interface BlockEvents {
    EventGroup GROUP = EventGroup.of("BlockEvents");

    TargetedEventHandler<BlockBreakEventJS> BROKEN =
            GROUP.targetedServer("broken", () -> BlockBreakEventJS.class);
    TargetedEventHandler<BlockRightClickEventJS> RIGHT_CLICKED =
            GROUP.targetedServer("rightClicked", () -> BlockRightClickEventJS.class);
    TargetedEventHandler<BlockPlaceEventJS> PLACED =
            GROUP.targetedServer("placed", () -> BlockPlaceEventJS.class);
    TargetedEventHandler<BlockLeftClickedEventJS> LEFT_CLICKED =
            GROUP.targetedServer("leftClicked", () -> BlockLeftClickedEventJS.class);
    
}