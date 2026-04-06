package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.api.event.EventBusForgeBridge;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.utils.event.dispatch.DispatchKey;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

public interface BlockEvents {
    EventGroup GROUP = EventGroup.of("BlockEvents");

    EventBusJS<BlockEvent.BreakEvent, String> BROKEN =
            GROUP.server("broken", BlockEvent.BreakEvent.class, DispatchKey.string());
    EventBusJS<PlayerInteractEvent.RightClickBlock, String> RIGHT_CLICKED =
            GROUP.server("rightClicked", PlayerInteractEvent.RightClickBlock.class, DispatchKey.string());
    EventBusJS<BlockEvent.EntityPlaceEvent, String> PLACED =
            GROUP.server("placed", BlockEvent.EntityPlaceEvent.class, DispatchKey.string());
    EventBusJS<PlayerInteractEvent.LeftClickBlock, String> LEFT_CLICKED =
            GROUP.server("leftClicked", PlayerInteractEvent.LeftClickBlock.class, DispatchKey.string());

    EventBusForgeBridge FORGE_BRIDGE = EventBusForgeBridge.create(NeoForge.EVENT_BUS)
            .bind(BROKEN)
            .bind(RIGHT_CLICKED)
            .bind(PLACED)
            .bind(LEFT_CLICKED);

}