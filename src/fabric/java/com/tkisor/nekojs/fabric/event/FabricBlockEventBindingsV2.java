package com.tkisor.nekojs.fabric.event;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.event.DispatchKey;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.bindings.event.BlockEvents;
import com.tkisor.nekojs.wrapper.event.block.BlockEntityTickEventJS;
import com.tkisor.nekojs.wrapper.event.block.BlockFarmlandTrampleEventJS;
import com.tkisor.nekojs.wrapper.event.block.BlockFluidPlacedEventJS;
import com.tkisor.nekojs.wrapper.event.block.BlockNeighborNotifyEventJS;
import com.tkisor.nekojs.wrapper.event.block.BlockPlacedEventJS;
import com.tkisor.nekojs.wrapper.event.block.BlockPortalSpawnEventJS;
import com.tkisor.nekojs.wrapper.event.block.BlockRandomTickEventJS;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.PortalShape;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.util.RandomSource;
import net.minecraft.server.level.ServerLevel;

/**
 * Fabric bridge v2 for block events: portalSpawn / neighborNotify / farmlandTrample /
 * placed / entityPlaced / fluidPlaced / randomTick / blockEntityTick.
 * NeoForge declares the native counterparts in that platform's BlockEvents implementation.
 * Fabric uses mixins where no Fabric API callback exists.
 *
 * <p>The buses declared here extend the Fabric {@link BlockEvents} {@code GROUP} (merged by
 * name with broken and v1's rightClicked/leftClicked) and dispatch by Block, the same key as
 * {@code state.getBlock()} in NeoForge's {@code dispatchByBlock}:
 * <ul>
 *   <li>{@code portalSpawn}: cancellable (cancelling stops the portal blocks from forming);</li>
 *   <li>{@code neighborNotify}: cancellable (cancelling drops this neighbor notification);</li>
 *   <li>{@code farmlandTrample}: cancellable (cancelling keeps the farmland from turning to dirt).</li>
 * </ul>
 *
 * <p><b>Initialization rule</b>: same as {@code FabricLevelEventBindingsV2}. The
 * {@code EventGroup} freezes after plugin bootstrap, so the {@code GROUP.add} calls for these
 * buses must happen before that freeze: {@code NekoJSFabricMod.onInitialize} calls
 * {@code bootstrap()} from {@code FabricBlockEventBindings.register()} early on.
 */
public final class FabricBlockEventBindingsV2 {

    private FabricBlockEventBindingsV2() {}

    /** Before a portal generates (server scripts, dispatched by the portal block; cancellable). */
    public static final EventBusJS<BlockPortalSpawnEventJS, Block> PORTAL_SPAWN =
            BlockEvents.GROUP.add("portalSpawn", ScriptType.SERVER, EventBusJS.of(
                    BlockPortalSpawnEventJS.class, true,
                    DispatchKey.of(Block.class, BlockPortalSpawnEventJS::getBlock)));

    /** A block receives a neighbor notification (server scripts, dispatched by the notified block;
     * cancellable). */
    public static final EventBusJS<BlockNeighborNotifyEventJS, Block> NEIGHBOR_NOTIFY =
            BlockEvents.GROUP.add("neighborNotify", ScriptType.SERVER, EventBusJS.of(
                    BlockNeighborNotifyEventJS.class, true,
                    DispatchKey.of(Block.class, BlockNeighborNotifyEventJS::getBlock)));

    /** Farmland is trampled (server scripts, dispatched by the farmland block; cancellable). */
    public static final EventBusJS<BlockFarmlandTrampleEventJS, Block> FARMLAND_TRAMPLE =
            BlockEvents.GROUP.add("farmlandTrample", ScriptType.SERVER, EventBusJS.of(
                    BlockFarmlandTrampleEventJS.class, true,
                    DispatchKey.of(Block.class, BlockFarmlandTrampleEventJS::getBlock)));

    /**
     * A block is placed (server scripts; cancellable). On 26.x, NeoForge binds both
     * placed/entityPlaced to {@code BlockEvent.EntityPlaceEvent}, whose only post site is the
     * enderman placement; fabric's {@code MixinEndermanLeaveBlockGoal} is the same, so one
     * placement posts to both buses. The dispatch key is the placed block (NeoForge uses the block
     * under the snapshot's feet); the difference is in the payload javadoc.
     */
    public static final EventBusJS<BlockPlacedEventJS, Block> PLACED =
            BlockEvents.GROUP.add("placed", ScriptType.SERVER, EventBusJS.of(
                    BlockPlacedEventJS.class, true,
                    DispatchKey.of(Block.class, BlockPlacedEventJS::getBlock)));

    /** An entity places a block (server scripts; cancellable); posts to both buses at the same site
     * as {@link #PLACED}. */
    public static final EventBusJS<BlockPlacedEventJS, Block> ENTITY_PLACED =
            BlockEvents.GROUP.add("entityPlaced", ScriptType.SERVER, EventBusJS.of(
                    BlockPlacedEventJS.class, true,
                    DispatchKey.of(Block.class, BlockPlacedEventJS::getBlock)));

    /**
     * A fluid hardens into a block (server scripts; cancellable). Covers the obsidian / cobblestone
     * / basalt conversion sites in LiquidBlock plus the spreadTo stone site in LavaFluid. This is
     * a superset of NeoForge 26.1.2, which covers none of the three LiquidBlock sites and two
     * fewer fire-spread sites; the difference is recorded in the port log.
     */
    public static final EventBusJS<BlockFluidPlacedEventJS, Block> FLUID_PLACED =
            BlockEvents.GROUP.add("fluidPlaced", ScriptType.SERVER, EventBusJS.of(
                    BlockFluidPlacedEventJS.class, true,
                    DispatchKey.of(Block.class, BlockFluidPlacedEventJS::getBlock)));

    /** A block random-ticks (server scripts, dispatched by block; not cancellable; high frequency, so
     * it short-circuits when there are no listeners). */
    public static final EventBusJS<BlockRandomTickEventJS, Block> RANDOM_TICK =
            BlockEvents.GROUP.server("randomTick", BlockRandomTickEventJS.class,
                    DispatchKey.of(Block.class, BlockRandomTickEventJS::getBlock));

    /** A block entity ticks (server scripts, dispatched by BlockEntityType; not cancellable; high
     * frequency). */
    public static final EventBusJS<BlockEntityTickEventJS, BlockEntityType<?>> BLOCK_ENTITY_TICK =
            BlockEvents.GROUP.server("blockEntityTick", BlockEntityTickEventJS.class,
                    DispatchKey.of(BlockEntityType.class, BlockEntityTickEventJS::getType));

    /** Initializes this class so its buses register into {@code BlockEvents.GROUP} (see the freeze
     * rule in the class comment). */
    public static void bootstrap() {
        // Empty body: touching any bus constant initializes the class.
    }

    /**
     * Before a portal generates: {@code MixinPortalShape} calls this at the HEAD of
     * {@code PortalShape#createPortalBlocks(LevelAccessor)}.
     *
     * @return true if a listener cancelled the portal generation, in which case the mixin should
     *         call {@code ci.cancel()}
     */
    public static boolean postPortalSpawn(LevelAccessor level, BlockPos pos, BlockState state,
                                          PortalShape portalSize) {
        if (!PORTAL_SPAWN.hasListeners()) {
            return false;
        }
        return PORTAL_SPAWN.post(new BlockPortalSpawnEventJS(level, pos, state, portalSize),
                state.getBlock());
    }

    /**
     * A block receives a neighbor notification: {@code MixinNeighborUpdater} calls this at the HEAD
     * of {@code NeighborUpdater#executeUpdate(Level, BlockState, BlockPos, Block,
     * Orientation, boolean)}. On 26.x both single-neighbor changes and updateNeighborsAt funnel
     * into this point, as javap shows.
     *
     * @return true if a listener cancelled this notification, in which case the mixin should call
     *         {@code ci.cancel()}
     */
    public static boolean postNeighborNotify(Level level, BlockState state, BlockPos pos,
                                             Block sourceBlock, Orientation orientation,
                                             boolean forceRedstone) {
        if (!NEIGHBOR_NOTIFY.hasListeners()) {
            return false;
        }
        return NEIGHBOR_NOTIFY.post(
                new BlockNeighborNotifyEventJS(level, pos, state, sourceBlock, orientation,
                        forceRedstone),
                state.getBlock());
    }

    /**
     * Farmland is trampled: {@code MixinFarmlandBlock} calls this at the {@code turnToDirt} INVOKE
     * point of {@code FarmlandBlock#fallOn}, which is only reached after the vanilla check passes
     * (javap shows this).
     *
     * @return true if a listener cancelled the trampling, in which case the mixin should call
     *         {@code ci.cancel()} and {@code turnToDirt} does not run
     */
    public static boolean postFarmlandTrample(Level level, BlockPos pos, BlockState state,
                                              float fallDistance, Entity entity) {
        if (!FARMLAND_TRAMPLE.hasListeners()) {
            return false;
        }
        return FARMLAND_TRAMPLE.post(
                new BlockFarmlandTrampleEventJS(level, pos, state, fallDistance, entity),
                state.getBlock());
    }

    /**
     * A block is placed: {@code MixinEndermanLeaveBlockGoal} calls this at the HEAD of
     * {@code canPlaceBlock} (javap shows that the only post site of {@code EntityPlaceEvent} in
     * NeoForge 26.1.2 is the same place). One placement posts to both the placed and entityPlaced
     * buses, the same as NeoForge's double binding, and both share one read-only payload instance.
     *
     * @return true if a listener on either bus cancelled the placement, in which case the mixin
     *         should call {@code cir.setReturnValue(false)}: the placement does not happen and the
     *         entity keeps the block
     */
    public static boolean postPlaced(Level level, BlockPos pos, BlockState state,
                                     BlockState placedAgainst, Entity entity) {
        if (!PLACED.hasListeners() && !ENTITY_PLACED.hasListeners()) {
            return false;
        }
        BlockPlacedEventJS payload = new BlockPlacedEventJS(level, pos, state, placedAgainst, entity);
        // No short-circuit: both buses must receive the event (NeoForge posts the same event object to
        // both bindings and they share the cancelled state).
        boolean cancelled = PLACED.post(payload, state.getBlock());
        cancelled |= ENTITY_PLACED.post(payload, state.getBlock());
        return cancelled;
    }

    /**
     * A fluid hardens into a block: {@code MixinLiquidBlock} (the obsidian / cobblestone / basalt
     * conversion sites) and {@code MixinLavaFluid} (the spreadTo stone site) call this at their
     * respective setBlock INVOKE points.
     *
     * @return true if a listener cancelled the hardening, which for the mixins means the fluid does
     *         not convert and keeps flowing
     */
    public static boolean postFluidPlaced(LevelAccessor level, BlockPos pos,
                                          BlockState newState, BlockState oldState) {
        if (!FLUID_PLACED.hasListeners()) {
            return false;
        }
        return FLUID_PLACED.post(new BlockFluidPlacedEventJS(level, pos, newState, oldState),
                newState.getBlock());
    }

    /**
     * A block random-ticks: {@code MixinBlockBehaviourRandomTick} calls this at the HEAD of
     * {@code BlockBehaviour.BlockStateBase#randomTick}, the twin of the existing NeoForge mixin.
     * Vanilla overrides bypass the interface's default randomTick.
     */
    public static void postRandomTick(ServerLevel level, BlockPos pos, BlockState state,
                                      RandomSource random) {
        if (!RANDOM_TICK.hasListeners()) {
            return;
        }
        RANDOM_TICK.post(new BlockRandomTickEventJS(level, pos, state, random), state.getBlock());
    }

    /**
     * A block entity ticks: {@code MixinLevelChunkBoundTickingBlockEntity} calls this at the HEAD
     * of {@code LevelChunk$BoundTickingBlockEntity#tick}; the mixin filters out invalid entities.
     */
    public static void postBlockEntityTick(BlockEntity blockEntity) {
        if (!BLOCK_ENTITY_TICK.hasListeners()) {
            return;
        }
        BLOCK_ENTITY_TICK.post(new BlockEntityTickEventJS(blockEntity), blockEntity.getType());
    }
}
