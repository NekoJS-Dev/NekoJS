package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.api.event.EventBusForgeBridge;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.utils.event.dispatch.DispatchKey;
import com.tkisor.nekojs.wrapper.event.block.BlockBreakEventJS;
import com.tkisor.nekojs.wrapper.event.block.BlockLeftClickedEventJS;
import com.tkisor.nekojs.wrapper.event.block.BlockPlaceEventJS;
import com.tkisor.nekojs.wrapper.event.block.BlockRightClickEventJS;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

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

    EventBusForgeBridge FORGE_BRIDGE = EventBusForgeBridge.create(NeoForge.EVENT_BUS)
            .bindTransformed(BROKEN, BlockBreakEventJS::new, BlockEvent.BreakEvent.class)
            .bindTransformed(RIGHT_CLICKED, BlockRightClickEventJS::new, PlayerInteractEvent.RightClickBlock.class)
            .bindTransformed(PLACED, BlockPlaceEventJS::new, BlockEvent.EntityPlaceEvent.class)
            .bindTransformed(LEFT_CLICKED, BlockLeftClickedEventJS::new, PlayerInteractEvent.LeftClickBlock.class);

}