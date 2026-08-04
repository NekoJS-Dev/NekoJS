package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.api.event.EventBusForgeBridge;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.api.event.DispatchKey;
import com.tkisor.nekojs.eventbus.EventBusFactory;
import com.tkisor.nekojs.wrapper.event.block.BlockEventJS;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BlockEvent;

public interface BlockEvents {
    EventGroup GROUP = EventGroup.of("BlockEvents");

    // 跨平台统一 wrapper：EventBusJS 声明为 BlockEventJS，dispatch key 从 wrapper 提取
    EventBusJS<BlockEventJS, Block> BROKEN =
            GROUP.server("broken", BlockEventJS.class, dispatchByBlock());
    EventBusJS<BlockEventJS, Block> ENTITY_PLACED =
            GROUP.server("entityPlaced", BlockEventJS.class, dispatchByBlock());
    EventBusJS<BlockEventJS, Block> ENTITY_MULTI_PLACED =
            GROUP.server("entityMultiPlaced", BlockEventJS.class, dispatchByBlock());
    EventBusJS<BlockEventJS, Block> NEIGHBOR_NOTIFY =
            GROUP.server("neighborNotify", BlockEventJS.class, dispatchByBlock());
    EventBusJS<BlockEventJS, Block> FLUID_PLACED =
            GROUP.server("fluidPlaced", BlockEventJS.class, dispatchByBlock());
    EventBusJS<BlockEventJS, Block> FARMLAND_TRAMPLE =
            GROUP.server("farmlandTrample", BlockEventJS.class, dispatchByBlock());
    EventBusJS<BlockEventJS, Block> PORTAL_SPAWN =
            GROUP.server("portalSpawn", BlockEventJS.class, dispatchByBlock());

    EventBusJS<BlockEventJS, Block> RIGHT_CLICKED =
            GROUP.server("rightClicked", BlockEventJS.class, dispatchByBlock());
    EventBusJS<BlockEventJS, Block> PLACED =
            GROUP.server("placed", BlockEventJS.class, dispatchByBlock());
    EventBusJS<BlockEventJS, Block> LEFT_CLICKED =
            GROUP.server("leftClicked", BlockEventJS.class, dispatchByBlock());

    EventBusJS<BlockEvent.HarvestDropsEvent, Block> HARVEST_DROPS =
            GROUP.server("harvestDrops", BlockEvent.HarvestDropsEvent.class,
                    EventBusFactory.createDispatchKey(Block.class, e -> e.getState().getBlock()));

    private static DispatchKey<BlockEventJS, Block> dispatchByBlock() {
        return EventBusFactory.createDispatchKey(Block.class, BlockEventJS::getBlock);
    }

    // —— 1.12.2 BlockEvent → BlockEventJS transformer ——
    // 统一字段名：level（从 getWorld() 映射）、player、pos、state、block
    private static BlockEventJS fromBlockEvent(BlockEvent event) {
        return new BlockEventJS(event.getWorld(), event.getPos(), event.getState(),
                event.getState().getBlock(), null);
    }

    EventBusForgeBridge FORGE_BRIDGE = EventBusForgeBridge.create(MinecraftForge.EVENT_BUS)
        .bindTransformed(BROKEN, (BlockEvent.BreakEvent e) ->
                fromBlockEvent(e).withExpToDrop(e.getExpToDrop()), BlockEvent.BreakEvent.class)
        .bindTransformed(PLACED, e ->
                new BlockEventJS(e.getWorld(), e.getPos(), e.getPlacedBlock(),
                        e.getPlacedBlock().getBlock(), e.getPlayer()), BlockEvent.PlaceEvent.class)
        .bindTransformed(ENTITY_PLACED, e ->
                fromBlockEvent(e).withEntity(e.getEntity()), BlockEvent.EntityPlaceEvent.class)
        .bindTransformed(ENTITY_MULTI_PLACED, e ->
                fromBlockEvent(e).withEntity(e.getEntity()), BlockEvent.EntityMultiPlaceEvent.class)
        .bindTransformed(NEIGHBOR_NOTIFY, BlockEvents::fromBlockEvent, BlockEvent.NeighborNotifyEvent.class)
        .bindTransformed(FLUID_PLACED, e ->
                new BlockEventJS(e.getWorld(), e.getPos(), e.getNewState(),
                        e.getNewState().getBlock(), null), BlockEvent.FluidPlaceBlockEvent.class)
        .bindTransformed(FARMLAND_TRAMPLE, e ->
                fromBlockEvent(e).withFallDistance(e.getFallDistance()).withEntity(e.getEntity()),
                BlockEvent.FarmlandTrampleEvent.class)
        .bindTransformed(PORTAL_SPAWN, BlockEvents::fromBlockEvent, BlockEvent.PortalSpawnEvent.class)
        .bindTransformed(RIGHT_CLICKED, BlockEvents::fromInteractEvent, PlayerInteractEvent.RightClickBlock.class)
        .bindTransformed(LEFT_CLICKED, BlockEvents::fromInteractEvent, PlayerInteractEvent.LeftClickBlock.class)
        .bind(HARVEST_DROPS);

    /** PlayerInteractEvent.RightClickBlock/LeftClickBlock → BlockEventJS */
    private static BlockEventJS fromInteractEvent(PlayerInteractEvent e) {
        var state = e.getWorld().getBlockState(e.getPos());
        return new BlockEventJS(e.getWorld(), e.getPos(), state, state.getBlock(),
                e.getEntityPlayer())
                .withItem(e.getItemStack(), e.getHand());
    }
}
