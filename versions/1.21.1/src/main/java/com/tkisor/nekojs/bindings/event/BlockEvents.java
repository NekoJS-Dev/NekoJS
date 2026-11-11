package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.event.DispatchKey;
import com.tkisor.nekojs.api.event.EventBusForgeBridge;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.event.level.BlockEntityTickEvent;
import com.tkisor.nekojs.event.level.RandomTickEvent;
import com.tkisor.nekojs.wrapper.event.block.BlockBrokenEventJS;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.BlockEvent.BreakEvent;

import java.util.function.Function;

/** Declares the complete NeoForge block event group and its native bridge. */
public final class BlockEvents {
    private BlockEvents() {}

    public static final EventGroup GROUP = EventGroup.of("BlockEvents");

    public static final EventBusJS<BlockBrokenEventJS, Block> BROKEN =
            GROUP.add("broken", ScriptType.SERVER, EventBusJS.of(
                    BlockBrokenEventJS.class, true, dispatchByBlock(BlockBrokenEventJS::getBlock)));

    public static final EventBusJS<BlockEvent.EntityPlaceEvent, Block> ENTITY_PLACED =
            GROUP.server("entityPlaced", BlockEvent.EntityPlaceEvent.class, dispatchByBlock());
    public static final EventBusJS<BlockEvent.EntityMultiPlaceEvent, Block> ENTITY_MULTI_PLACED =
            GROUP.server("entityMultiPlaced", BlockEvent.EntityMultiPlaceEvent.class, dispatchByBlock());
    public static final EventBusJS<BlockEvent.NeighborNotifyEvent, Block> NEIGHBOR_NOTIFY =
            GROUP.server("neighborNotify", BlockEvent.NeighborNotifyEvent.class, dispatchByBlock());
    public static final EventBusJS<BlockEvent.FluidPlaceBlockEvent, Block> FLUID_PLACED =
            GROUP.server("fluidPlaced", BlockEvent.FluidPlaceBlockEvent.class, dispatchByBlock());
    public static final EventBusJS<BlockEvent.FarmlandTrampleEvent, Block> FARMLAND_TRAMPLE =
            GROUP.server("farmlandTrample", BlockEvent.FarmlandTrampleEvent.class, dispatchByBlock());
    public static final EventBusJS<BlockEvent.PortalSpawnEvent, Block> PORTAL_SPAWN =
            GROUP.server("portalSpawn", BlockEvent.PortalSpawnEvent.class, dispatchByBlock());
    public static final EventBusJS<BlockEvent.BlockToolModificationEvent, Block> TOOL_MODIFICATION =
            GROUP.server("toolModification", BlockEvent.BlockToolModificationEvent.class, dispatchByBlock());

    public static final EventBusJS<PlayerInteractEvent.RightClickBlock, Block> RIGHT_CLICKED =
            GROUP.server("rightClicked", PlayerInteractEvent.RightClickBlock.class,
                    dispatchByBlock(e -> e.getLevel().getBlockState(e.getPos()).getBlock()));
    public static final EventBusJS<BlockEvent.EntityPlaceEvent, Block> PLACED =
            GROUP.server("placed", BlockEvent.EntityPlaceEvent.class, dispatchByBlock());
    public static final EventBusJS<PlayerInteractEvent.LeftClickBlock, Block> LEFT_CLICKED =
            GROUP.server("leftClicked", PlayerInteractEvent.LeftClickBlock.class,
                    dispatchByBlock(e -> e.getLevel().getBlockState(e.getPos()).getBlock()));

    /** Receives the mixin-posted native random tick event. */
    public static final EventBusJS<RandomTickEvent, Block> RANDOM_TICK =
            GROUP.server("randomTick", RandomTickEvent.class,
                    dispatchByBlock(RandomTickEvent::getBlock));

    /** Receives the mixin-posted native block entity tick event. */
    public static final EventBusJS<BlockEntityTickEvent, BlockEntityType<?>> BLOCK_ENTITY_TICK =
            GROUP.server("blockEntityTick", BlockEntityTickEvent.class,
                    DispatchKey.of(BlockEntityType.class, BlockEntityTickEvent::getType));

    static <T> DispatchKey<T, Block> dispatchByBlock(Function<T, Block> toKey) {
        return DispatchKey.of(Block.class, toKey);
    }

    private static <T extends BlockEvent> DispatchKey<T, Block> dispatchByBlock() {
        return dispatchByBlock(event -> event.getState().getBlock());
    }

    public static final EventBusForgeBridge FORGE_BRIDGE = EventBusForgeBridge.create(NeoForge.EVENT_BUS)
        .bindTransformed(
                BROKEN,
                (BreakEvent event) -> new BlockBrokenEventJS(
                        event.getLevel(), event.getPos(), event.getState(), event.getPlayer()),
                BreakEvent.class)
        .bind(ENTITY_PLACED)
        .bind(ENTITY_MULTI_PLACED)
        .bind(NEIGHBOR_NOTIFY)
        .bind(FLUID_PLACED)
        .bind(FARMLAND_TRAMPLE)
        .bind(PORTAL_SPAWN)
        .bind(TOOL_MODIFICATION)
        // Interaction callbacks also fire on the client; these buses accept server instances only.
        .bind(RIGHT_CLICKED, e -> !e.getLevel().isClientSide())
        .bind(PLACED)
        .bind(LEFT_CLICKED, e -> !e.getLevel().isClientSide())
        .bind(RANDOM_TICK)
        .bind(BLOCK_ENTITY_TICK);

}
