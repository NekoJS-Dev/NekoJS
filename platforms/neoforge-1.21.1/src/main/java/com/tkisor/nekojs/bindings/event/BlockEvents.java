package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.api.event.EventBusForgeBridge;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.api.event.DispatchKey;
import com.tkisor.nekojs.eventbus.EventBusFactory;
import com.tkisor.nekojs.event.level.BlockEntityTickEvent;
import com.tkisor.nekojs.event.level.RandomTickEvent;
import com.tkisor.nekojs.wrapper.event.block.BlockEventJS;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.BlockEvent.BreakEvent;

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

    /** 方块随机 tick（仅对 {@code isRandomlyTicking()} 的方块触发；由 mixin 注入，按 Block 分发）。 */
    EventBusJS<RandomTickEvent, Block> RANDOM_TICK =
            GROUP.server("randomTick", RandomTickEvent.class, dispatchByBlockRaw(RandomTickEvent::getBlock));

    /** 方块实体 tick（所有有 ticker 的方块实体，按 BlockEntityType 分发）。 */
    EventBusJS<BlockEntityTickEvent, BlockEntityType<?>> BLOCK_ENTITY_TICK =
            GROUP.server("blockEntityTick", BlockEntityTickEvent.class,
                    EventBusFactory.createDispatchKey(BlockEntityType.class, BlockEntityTickEvent::getType));

    private static DispatchKey<BlockEventJS, Block> dispatchByBlock() {
        return EventBusFactory.createDispatchKey(Block.class, BlockEventJS::getBlock);
    }

    private static <T> DispatchKey<T, Block> dispatchByBlockRaw(java.util.function.Function<T, Block> toKey) {
        return EventBusFactory.createDispatchKey(Block.class, toKey);
    }

    // —— NeoForge BlockEvent → BlockEventJS transformer ——
    private static BlockEventJS fromBlockEvent(BlockEvent event) {
        return new BlockEventJS(event.getLevel(), event.getPos(), event.getState(),
                event.getState().getBlock(), null);
    }

    EventBusForgeBridge FORGE_BRIDGE = EventBusForgeBridge.create(NeoForge.EVENT_BUS)
        .bindTransformed(BROKEN, (BreakEvent e) ->
                new BlockEventJS(e.getLevel(), e.getPos(), e.getState(),
                        e.getState().getBlock(), e.getPlayer()), BreakEvent.class)
        .bindTransformed(ENTITY_PLACED, e ->
                fromBlockEvent(e).withEntity(e.getEntity()), BlockEvent.EntityPlaceEvent.class)
        .bindTransformed(ENTITY_MULTI_PLACED, e ->
                fromBlockEvent(e).withEntity(e.getEntity()), BlockEvent.EntityMultiPlaceEvent.class)
        .bindTransformed(NEIGHBOR_NOTIFY, BlockEvents::fromBlockEvent, BlockEvent.NeighborNotifyEvent.class)
        .bindTransformed(FLUID_PLACED, e ->
                new BlockEventJS(e.getLevel(), e.getPos(), e.getNewState(),
                        e.getNewState().getBlock(), null), BlockEvent.FluidPlaceBlockEvent.class)
        .bindTransformed(FARMLAND_TRAMPLE, e ->
                fromBlockEvent(e).withFallDistance((float) e.getFallDistance()),
                BlockEvent.FarmlandTrampleEvent.class)
        .bindTransformed(PORTAL_SPAWN, BlockEvents::fromBlockEvent, BlockEvent.PortalSpawnEvent.class)
        .bindTransformed(RIGHT_CLICKED, BlockEvents::fromInteractEvent, PlayerInteractEvent.RightClickBlock.class)
        .bindTransformed(PLACED, e ->
                fromBlockEvent(e).withEntity(e.getEntity()), BlockEvent.EntityPlaceEvent.class)
        .bindTransformed(LEFT_CLICKED, BlockEvents::fromInteractEvent, PlayerInteractEvent.LeftClickBlock.class)
        .bind(RANDOM_TICK)
        .bind(BLOCK_ENTITY_TICK);

    /** PlayerInteractEvent.RightClickBlock/LeftClickBlock → BlockEventJS */
    private static BlockEventJS fromInteractEvent(PlayerInteractEvent e) {
        var state = e.getLevel().getBlockState(e.getPos());
        return new BlockEventJS(e.getLevel(), e.getPos(), state, state.getBlock(),
                e.getEntity() instanceof Player p ? p : null)
                .withItem(e.getItemStack(), e.getHand());
    }
}
