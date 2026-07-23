package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.api.event.EventBusForgeBridge;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.api.event.DispatchKey;
import com.tkisor.nekojs.eventbus.EventBusFactory;
import net.minecraft.block.Block;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BlockEvent;

public interface BlockEvents {
    EventGroup GROUP = EventGroup.of("BlockEvents");

    EventBusJS<BlockEvent.BreakEvent, Block> BREAK =
            GROUP.server("blockBreak", BlockEvent.BreakEvent.class,
                    EventBusFactory.createDispatchKey(Block.class,
                            e -> e.getState().getBlock()));
    EventBusJS<BlockEvent.PlaceEvent, Block> PLACE =
            GROUP.server("place", BlockEvent.PlaceEvent.class,
                    EventBusFactory.createDispatchKey(Block.class,
                            e -> e.getPlacedBlock().getBlock()));
    // entityPlaced：等价于 neoforge 的 placed，绑定 EntityPlaceEvent
    EventBusJS<BlockEvent.EntityPlaceEvent, Block> ENTITY_PLACED =
            GROUP.server("entityPlaced", BlockEvent.EntityPlaceEvent.class,
                    EventBusFactory.createDispatchKey(Block.class,
                            e -> e.getState().getBlock()));
    EventBusJS<BlockEvent.EntityMultiPlaceEvent, Block> ENTITY_MULTI_PLACED =
            GROUP.server("entityMultiPlaced", BlockEvent.EntityMultiPlaceEvent.class,
                    EventBusFactory.createDispatchKey(Block.class,
                            e -> e.getState().getBlock()));
    EventBusJS<BlockEvent.NeighborNotifyEvent, Block> NEIGHBOR_NOTIFY =
            GROUP.server("neighborNotify", BlockEvent.NeighborNotifyEvent.class,
                    EventBusFactory.createDispatchKey(Block.class,
                            e -> e.getState().getBlock()));
    EventBusJS<BlockEvent.FluidPlaceBlockEvent, Block> FLUID_PLACED =
            GROUP.server("fluidPlaced", BlockEvent.FluidPlaceBlockEvent.class,
                    EventBusFactory.createDispatchKey(Block.class,
                            e -> e.getNewState().getBlock()));
    EventBusJS<BlockEvent.FarmlandTrampleEvent, Block> FARMLAND_TRAMPLE =
            GROUP.server("farmlandTrample", BlockEvent.FarmlandTrampleEvent.class,
                    EventBusFactory.createDispatchKey(Block.class,
                            e -> e.getState().getBlock()));
    EventBusJS<BlockEvent.PortalSpawnEvent, Block> PORTAL_SPAWN =
            GROUP.server("portalSpawn", BlockEvent.PortalSpawnEvent.class,
                    EventBusFactory.createDispatchKey(Block.class,
                            e -> e.getState().getBlock()));
    EventBusJS<BlockEvent.HarvestDropsEvent, Block> HARVEST_DROPS =
            GROUP.server("harvestDrops", BlockEvent.HarvestDropsEvent.class,
                    EventBusFactory.createDispatchKey(Block.class,
                            e -> e.getState().getBlock()));
    // 交互事件：PlayerInteractEvent 的 key 取自被交互方块
    EventBusJS<PlayerInteractEvent.RightClickBlock, Block> RIGHT_CLICKED =
            GROUP.server("rightClicked", PlayerInteractEvent.RightClickBlock.class,
                    EventBusFactory.createDispatchKey(Block.class,
                            e -> e.getWorld().getBlockState(e.getPos()).getBlock()));
    EventBusJS<PlayerInteractEvent.LeftClickBlock, Block> LEFT_CLICKED =
            GROUP.server("leftClicked", PlayerInteractEvent.LeftClickBlock.class,
                    EventBusFactory.createDispatchKey(Block.class,
                            e -> e.getWorld().getBlockState(e.getPos()).getBlock()));

    EventBusForgeBridge FORGE_BRIDGE = EventBusForgeBridge.create(MinecraftForge.EVENT_BUS)
            .bind(BREAK)
            .bind(PLACE)
            .bind(ENTITY_PLACED)
            .bind(ENTITY_MULTI_PLACED)
            .bind(NEIGHBOR_NOTIFY)
            .bind(FLUID_PLACED)
            .bind(FARMLAND_TRAMPLE)
            .bind(PORTAL_SPAWN)
            .bind(HARVEST_DROPS)
            .bind(RIGHT_CLICKED)
            .bind(LEFT_CLICKED);
}
