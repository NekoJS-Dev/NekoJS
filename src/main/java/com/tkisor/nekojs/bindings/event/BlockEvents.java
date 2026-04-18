package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.api.event.EventBusForgeBridge;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.utils.event.dispatch.DispatchKey;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.function.Function;

public interface BlockEvents {
    EventGroup GROUP = EventGroup.of("BlockEvents");

    EventBusJS<BlockEvent.BreakEvent, Block> BROKEN =
            GROUP.server("broken", BlockEvent.BreakEvent.class, dispatchByBlock());
    EventBusJS<BlockEvent.EntityPlaceEvent, Block> ENTITY_PLACED =
            GROUP.server("entityPlaced", BlockEvent.EntityPlaceEvent.class, dispatchByBlock());
    EventBusJS<BlockEvent.EntityMultiPlaceEvent, Block> ENTITY_MULTI_PLACED =
            GROUP.server("entityMultiPlaced", BlockEvent.EntityMultiPlaceEvent.class, dispatchByBlock());
    EventBusJS<BlockEvent.NeighborNotifyEvent, Block> NEIGHBOR_NOTIFY =
            GROUP.server("neighborNotify", BlockEvent.NeighborNotifyEvent.class, dispatchByBlock());
    EventBusJS<BlockEvent.FluidPlaceBlockEvent, Block> FLUID_PLACED =
            GROUP.server("fluidPlaced", BlockEvent.FluidPlaceBlockEvent.class, dispatchByBlock());
    EventBusJS<BlockEvent.FarmlandTrampleEvent, Block> FARMLAND_TRAMPLE =
            GROUP.server("farmlandTrample", BlockEvent.FarmlandTrampleEvent.class, dispatchByBlock());
    EventBusJS<BlockEvent.PortalSpawnEvent, Block> PORTAL_SPAWN =
            GROUP.server("portalSpawn", BlockEvent.PortalSpawnEvent.class, dispatchByBlock());
    EventBusJS<BlockEvent.BlockToolModificationEvent, Block> TOOL_TOOL_MODIFICATION =
            GROUP.server("toolModification", BlockEvent.BlockToolModificationEvent.class, dispatchByBlock());

    EventBusJS<PlayerInteractEvent.RightClickBlock, Block> RIGHT_CLICKED =
            GROUP.server("rightClicked", PlayerInteractEvent.RightClickBlock.class, dispatchByBlock(e -> e.getLevel().getBlockState(e.getPos()).getBlock()));
    EventBusJS<BlockEvent.EntityPlaceEvent, Block> PLACED =
            GROUP.server("placed", BlockEvent.EntityPlaceEvent.class, dispatchByBlock());
    EventBusJS<PlayerInteractEvent.LeftClickBlock, Block> LEFT_CLICKED =
            GROUP.server("leftClicked", PlayerInteractEvent.LeftClickBlock.class, dispatchByBlock(e -> e.getLevel().getBlockState(e.getPos()).getBlock()));


    private static <T> DispatchKey<T, Block> dispatchByBlock(Function<T, Block> toKey) {
        return DispatchKey.of(Block.class, toKey);
    }

    private static <T extends BlockEvent> DispatchKey<T, Block> dispatchByBlock() {
        return dispatchByBlock(event -> event.getState().getBlock());
    }

    EventBusForgeBridge FORGE_BRIDGE = EventBusForgeBridge.create(NeoForge.EVENT_BUS)
        .bind(BROKEN)
        .bind(ENTITY_PLACED)
        .bind(ENTITY_MULTI_PLACED)
        .bind(NEIGHBOR_NOTIFY)
        .bind(FLUID_PLACED)
        .bind(FARMLAND_TRAMPLE)
        .bind(PORTAL_SPAWN)
        .bind(TOOL_TOOL_MODIFICATION)
        .bind(RIGHT_CLICKED)
        .bind(PLACED)
        .bind(LEFT_CLICKED);
}