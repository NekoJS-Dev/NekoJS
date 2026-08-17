package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.api.event.EventBusForgeBridge;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.api.event.DispatchKey;
import com.tkisor.nekojs.eventbus.EventBusFactory;
import com.tkisor.nekojs.event.level.BlockEntityTickEvent;
import com.tkisor.nekojs.event.level.RandomTickEvent;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;

import java.util.function.Function;

public interface BlockEvents {
    EventGroup GROUP = EventGroup.of("BlockEvents");

    EventBusJS<BreakBlockEvent, Block> BROKEN =
            GROUP.server("broken", BreakBlockEvent.class, dispatchByBlock());
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
    EventBusJS<BlockEvent.BlockToolModificationEvent, Block> TOOL_MODIFICATION =
            GROUP.server("toolModification", BlockEvent.BlockToolModificationEvent.class, dispatchByBlock());

    EventBusJS<PlayerInteractEvent.RightClickBlock, Block> RIGHT_CLICKED =
            GROUP.server("rightClicked", PlayerInteractEvent.RightClickBlock.class, dispatchByBlock(e -> e.getLevel().getBlockState(e.getPos()).getBlock()));
    EventBusJS<BlockEvent.EntityPlaceEvent, Block> PLACED =
            GROUP.server("placed", BlockEvent.EntityPlaceEvent.class, dispatchByBlock());
    EventBusJS<PlayerInteractEvent.LeftClickBlock, Block> LEFT_CLICKED =
            GROUP.server("leftClicked", PlayerInteractEvent.LeftClickBlock.class, dispatchByBlock(e -> e.getLevel().getBlockState(e.getPos()).getBlock()));

    /** 方块随机 tick（仅对 {@code isRandomlyTicking()} 的方块触发；由 mixin 注入，按 Block 分发）。 */
    EventBusJS<RandomTickEvent, Block> RANDOM_TICK =
            GROUP.server("randomTick", RandomTickEvent.class, dispatchByBlock(RandomTickEvent::getBlock));

    /** 方块实体 tick（所有有 ticker 的方块实体，按 BlockEntityType 分发）。 */
    EventBusJS<BlockEntityTickEvent, BlockEntityType<?>> BLOCK_ENTITY_TICK =
            GROUP.server("blockEntityTick", BlockEntityTickEvent.class,
                    EventBusFactory.createDispatchKey(BlockEntityType.class, BlockEntityTickEvent::getType));

    private static <T> DispatchKey<T, Block> dispatchByBlock(Function<T, Block> toKey) {
        return EventBusFactory.createDispatchKey(Block.class, toKey);
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
        .bind(TOOL_MODIFICATION)
        // PlayerInteract 双逻辑侧触发：SERVER 总线只投递服务端实例（客户端交互在 Render 线程）
        .bind(RIGHT_CLICKED, e -> !e.getLevel().isClientSide())
        .bind(PLACED)
        .bind(LEFT_CLICKED, e -> !e.getLevel().isClientSide())
        .bind(RANDOM_TICK)
        .bind(BLOCK_ENTITY_TICK);
}