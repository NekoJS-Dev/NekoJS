package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.utils.event.dispatch.DispatchKey;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

public interface BlockEvents {
    EventGroup GROUP = EventGroup.of("BlockEvents");

    EventBusJS<BlockEvent.BreakEvent, Block> BROKEN =
            GROUP.server("broken", BlockEvent.BreakEvent.class, DispatchKey.block());
    EventBusJS<PlayerInteractEvent.RightClickBlock, Block> RIGHT_CLICKED =
            GROUP.server("rightClicked", PlayerInteractEvent.RightClickBlock.class, DispatchKey.block());
    EventBusJS<BlockEvent.EntityPlaceEvent, Block> PLACED =
            GROUP.server("placed", BlockEvent.EntityPlaceEvent.class, DispatchKey.block());
    EventBusJS<PlayerInteractEvent.LeftClickBlock, Block> LEFT_CLICKED =
            GROUP.server("leftClicked", PlayerInteractEvent.LeftClickBlock.class, DispatchKey.block());
}