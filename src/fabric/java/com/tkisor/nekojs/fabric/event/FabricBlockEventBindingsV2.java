package com.tkisor.nekojs.fabric.event;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.event.DispatchKey;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.StableBlockEvent;
import com.tkisor.nekojs.bindings.event.BlockEvents;
import com.tkisor.nekojs.bindings.event.ServerLevelEventThread;
import com.tkisor.nekojs.bindings.event.StableBlockPlacement;
import com.tkisor.nekojs.wrapper.event.block.BlockEntityTickEventJS;
import com.tkisor.nekojs.wrapper.event.block.BlockFarmlandTrampleEventJS;
import com.tkisor.nekojs.wrapper.event.block.BlockFluidPlacedEventJS;
import com.tkisor.nekojs.wrapper.event.block.BlockNeighborNotifyEventJS;
import com.tkisor.nekojs.wrapper.event.block.BlockPlacedEventJS;
import com.tkisor.nekojs.wrapper.event.block.BlockPortalSpawnEventJS;
import com.tkisor.nekojs.wrapper.event.block.BlockRandomTickEventJS;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.PortalShape;
import net.minecraft.world.level.redstone.Orientation;

/**
 * Fabric bridge v2 for block events: portalSpawn / neighborNotify / farmlandTrample /
 * placed / entityPlaced / fluidPlaced / randomTick / blockEntityTick, plus stable-only
 * entityMultiPlaced / toolModification.
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
                    DispatchKey.of(Block.class, BlockPortalSpawnEventJS::getBlock)), api -> {
                api.versioned().when(event -> !(event instanceof StablePortalSpawn));
                api.stable()
                        .when(event -> event instanceof StablePortalSpawn && ServerLevelEventThread.isCurrent(event.getLevel()))
                        .view(StableBlockEvent.class, FabricStableEventProjections::blockPortalSpawn)
                        .selector(String.class, FabricStableEventProjections::resolveBlockSelector)
                        .cancellable()
                        .scriptTypes(ScriptType.SERVER);
            });

    /** A block receives a neighbor notification (server scripts, dispatched by the notified block;
     * cancellable). */
    public static final EventBusJS<BlockNeighborNotifyEventJS, Block> NEIGHBOR_NOTIFY =
            BlockEvents.GROUP.add("neighborNotify", ScriptType.SERVER, EventBusJS.of(
                    BlockNeighborNotifyEventJS.class, true,
                    DispatchKey.of(Block.class, BlockNeighborNotifyEventJS::getBlock)), api -> {
                api.versioned().when(event -> !(event instanceof StableNeighborNotify));
                api.stable()
                        .when(event -> event instanceof StableNeighborNotify)
                        .view(StableBlockEvent.class, FabricStableEventProjections::blockNeighborNotify)
                        .selector(String.class, FabricStableEventProjections::resolveBlockSelector)
                        .cancellable()
                        .scriptTypes(ScriptType.SERVER);
            });

    /** Farmland is trampled (server scripts, dispatched by the farmland block; cancellable). */
    public static final EventBusJS<BlockFarmlandTrampleEventJS, Block> FARMLAND_TRAMPLE =
            BlockEvents.GROUP.add("farmlandTrample", ScriptType.SERVER, EventBusJS.of(
                    BlockFarmlandTrampleEventJS.class, true,
                    DispatchKey.of(Block.class, BlockFarmlandTrampleEventJS::getBlock)), api -> {
                api.versioned().when(event -> !(event instanceof StableFarmlandTrample));
                api.stable()
                        .when(event -> event instanceof StableFarmlandTrample
                                && ServerLevelEventThread.isCurrent(event.getLevel()))
                        .view(StableBlockEvent.class, FabricStableEventProjections::blockFarmlandTrample)
                        .selector(String.class, FabricStableEventProjections::resolveBlockSelector)
                        .cancellable()
                        .scriptTypes(ScriptType.SERVER);
            });

    public static final EventBusJS<BlockPlacedEventJS, Block> PLACED =
            BlockEvents.GROUP.add("placed", ScriptType.SERVER, EventBusJS.of(
                    BlockPlacedEventJS.class, true,
                    DispatchKey.of(Block.class, BlockPlacedEventJS::getBlock)), api -> {
                api.versioned().when(event -> !(event instanceof StablePlacement));
                api.stable()
                        .when(event -> event instanceof StablePlacement)
                        .view(StableBlockEvent.class, FabricStableEventProjections::blockPlaced)
                        .selector(String.class, FabricStableEventProjections::resolveBlockSelector)
                        .cancellable()
                        .scriptTypes(ScriptType.SERVER);
            });

    /** An entity places a block (server scripts; cancellable); posts to both buses at the same site
     * as {@link #PLACED}. */
    public static final EventBusJS<BlockPlacedEventJS, Block> ENTITY_PLACED =
            BlockEvents.GROUP.add("entityPlaced", ScriptType.SERVER, EventBusJS.of(
                    BlockPlacedEventJS.class, true,
                    DispatchKey.of(Block.class, BlockPlacedEventJS::getBlock)), api -> {
                api.versioned().when(event -> !(event instanceof StablePlacement));
                api.stable()
                        .when(event -> event instanceof StablePlacement)
                        .view(StableBlockEvent.class, FabricStableEventProjections::blockPlaced)
                        .selector(String.class, FabricStableEventProjections::resolveBlockSelector)
                        .cancellable()
                        .scriptTypes(ScriptType.SERVER);
            });

    /**
     * A fluid hardens into a block (server scripts; cancellable). Covers the obsidian / cobblestone
     * / basalt conversion sites in LiquidBlock plus the spreadTo stone site in LavaFluid. This is
     * a superset of NeoForge 26.1.2, which covers none of the three LiquidBlock sites and two
     * fewer fire-spread sites; the difference is recorded in the port log.
     */
    public static final EventBusJS<BlockFluidPlacedEventJS, Block> FLUID_PLACED =
            BlockEvents.GROUP.add("fluidPlaced", ScriptType.SERVER, EventBusJS.of(
                    BlockFluidPlacedEventJS.class, true,
                    DispatchKey.of(Block.class, BlockFluidPlacedEventJS::getBlock)), api -> {
                api.versioned().when(event -> !(event instanceof StableFluidPlacement));
                api.stable()
                        .when(event -> event instanceof StableFluidPlacement
                                && ServerLevelEventThread.isCurrent(event.getLevel()))
                        .view(StableBlockEvent.class, FabricStableEventProjections::blockFluidPlaced)
                        .selector(String.class, FabricStableEventProjections::resolveBlockSelector)
                        .cancellable()
                        .scriptTypes(ScriptType.SERVER);
            });

    /** A block random-ticks (server scripts, dispatched by block; not cancellable; high frequency, so
     * it short-circuits when there are no listeners). */
    public static final EventBusJS<BlockRandomTickEventJS, Block> RANDOM_TICK =
            BlockEvents.GROUP.server("randomTick", BlockRandomTickEventJS.class,
                    DispatchKey.of(Block.class, BlockRandomTickEventJS::getBlock), api -> {
                        api.versioned();
                        api.stable()
                                .when(event -> event.getLevel() != null && event.getLevel().getServer().isSameThread())
                                .view(StableBlockEvent.class, FabricStableEventProjections::blockRandomTick)
                                .selector(String.class, FabricStableEventProjections::resolveBlockSelector)
                                .scriptTypes(ScriptType.SERVER);
                    });

    /** A block entity ticks (server scripts, dispatched by BlockEntityType; not cancellable; high
     * frequency). */
    public static final EventBusJS<BlockEntityTickEventJS, BlockEntityType<?>> BLOCK_ENTITY_TICK =
            BlockEvents.GROUP.server("blockEntityTick", BlockEntityTickEventJS.class,
                    DispatchKey.of(BlockEntityType.class, BlockEntityTickEventJS::getType), api -> {
                        api.versioned().when(event -> !(event instanceof BlockEntityTickEventJS.Actual));
                        api.stable()
                                .when(event -> event instanceof BlockEntityTickEventJS.Actual)
                                .view(StableBlockEvent.class, FabricStableEventProjections::blockEntityTick)
                                .selector(String.class, FabricStableEventProjections::resolveBlockEntityTypeSelector)
                                .scriptTypes(ScriptType.SERVER);
                    });

    /** Multi-block placement (beds, doors, tall plants); published on the stable surface only. */
    public static final EventBusJS<StableBlockEvent, Block> ENTITY_MULTI_PLACED =
            BlockEvents.GROUP.add("entityMultiPlaced", ScriptType.SERVER, EventBusJS.of(
                    StableBlockEvent.class, true,
                    DispatchKey.of(Block.class, e -> BuiltInRegistries.BLOCK
                            .getOptional(FabricStableEventProjections.parseSelectorId(e.getBlockId()))
                            .orElse(null))),
                    api -> api.stable()
                            .selector(String.class, FabricStableEventProjections::resolveBlockSelector)
                            .scriptTypes(ScriptType.SERVER));

    /** Tool block modification (axe stripping/scraping/unwaxing, shovel flattening/dousing, hoe tilling);
     * published on the stable surface only. */
    public static final EventBusJS<StableBlockEvent, Block> TOOL_MODIFICATION =
            BlockEvents.GROUP.add("toolModification", ScriptType.SERVER, EventBusJS.of(
                    StableBlockEvent.class, true,
                    DispatchKey.of(Block.class, e -> BuiltInRegistries.BLOCK
                            .getOptional(FabricStableEventProjections.parseSelectorId(e.getBlockId()))
                            .orElse(null))),
                    api -> api.stable()
                            .selector(String.class, FabricStableEventProjections::resolveBlockSelector)
                            .scriptTypes(ScriptType.SERVER));

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
        return PORTAL_SPAWN.post(new BlockPortalSpawnEventJS(level, pos, state, portalSize), state.getBlock());
    }

    public static boolean postStablePortalSpawn(LevelAccessor level, BlockPos pos, PortalShape portalSize) {
        if (!PORTAL_SPAWN.hasStableModuleListeners(ScriptType.SERVER) || !ServerLevelEventThread.isCurrent(level)) return false;
        var state = level.getBlockState(pos);
        return PORTAL_SPAWN.postStableModuleCancellable(
                new StablePortalSpawn(level, pos, state, portalSize), state.getBlock(), ScriptType.SERVER);
    }

    private static final class StablePortalSpawn extends BlockPortalSpawnEventJS {
        private StablePortalSpawn(LevelAccessor level, BlockPos pos, BlockState state, PortalShape portalSize) {
            super(level, pos, state, portalSize);
        }
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
        return NEIGHBOR_NOTIFY.hasListeners() && NEIGHBOR_NOTIFY.post(
                new BlockNeighborNotifyEventJS(level, pos, state, sourceBlock, orientation, forceRedstone),
                state.getBlock());
    }

    public static boolean postNeighborNotify(ServerLevel level, BlockPos pos, Block sourceBlock,
                                             Orientation orientation) {
        if (!NEIGHBOR_NOTIFY.hasStableModuleListeners(ScriptType.SERVER) || !ServerLevelEventThread.isCurrent(level)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        return NEIGHBOR_NOTIFY.postStableModuleCancellable(
                new StableNeighborNotify(level, pos, state, sourceBlock, orientation), state.getBlock(), ScriptType.SERVER);
    }

    private static final class StableNeighborNotify extends BlockNeighborNotifyEventJS {
        private StableNeighborNotify(ServerLevel level, BlockPos pos, BlockState state, Block sourceBlock,
                                     Orientation orientation) {
            super(level, pos, state, sourceBlock, orientation, false);
        }
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

    public static boolean postStableFarmlandTrample(Level level, BlockPos pos, BlockState state,
                                                    float fallDistance, Entity entity) {
        return FARMLAND_TRAMPLE.hasStableModuleListeners(ScriptType.SERVER) && FARMLAND_TRAMPLE.postStableModuleCancellable(
                new StableFarmlandTrample(level, pos, state, fallDistance, entity), state.getBlock(), ScriptType.SERVER);
    }

    private static final class StableFarmlandTrample extends BlockFarmlandTrampleEventJS {
        private StableFarmlandTrample(Level level, BlockPos pos, BlockState state, float fallDistance, Entity entity) {
            super(level, pos, state, fallDistance, entity);
        }
    }

    public static boolean postPlaced(Level level, BlockPos pos, BlockState state,
                                     BlockState placedAgainst, Entity entity) {
        if (!PLACED.hasListeners() && !ENTITY_PLACED.hasListeners()) {
            return false;
        }
        BlockPlacedEventJS payload = new BlockPlacedEventJS(level, pos, state, placedAgainst, entity);
        return ENTITY_PLACED.post(payload, state.getBlock()) || PLACED.post(payload, state.getBlock());
    }

    public static BlockState stableFluidPlacementState(LevelAccessor level, BlockPos pos, BlockState state) {
        if (!ServerLevelEventThread.isCurrent(level) || !FLUID_PLACED.hasStableModuleListeners(ScriptType.SERVER)) {
            return state;
        }
        BlockState oldState = level.getBlockState(pos);
        return FLUID_PLACED.postStableModuleCancellable(
                new StableFluidPlacement(level, pos, state, oldState), state.getBlock(), ScriptType.SERVER)
                ? oldState : state;
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
        return FLUID_PLACED.hasListeners() && FLUID_PLACED.post(
                new BlockFluidPlacedEventJS(level, pos, newState, oldState), newState.getBlock());
    }

    private static final class StableFluidPlacement extends BlockFluidPlacedEventJS {
        private StableFluidPlacement(LevelAccessor level, BlockPos pos, BlockState newState, BlockState oldState) {
            super(level, pos, newState, oldState);
        }
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
        if (!(blockEntity.getLevel() instanceof ServerLevel) || !BLOCK_ENTITY_TICK.hasListeners()) {
            return;
        }
        BLOCK_ENTITY_TICK.post(new BlockEntityTickEventJS(blockEntity), blockEntity.getType());
    }

    public static void postActualBlockEntityTick(ServerLevel level, BlockPos pos, BlockEntity blockEntity) {
        if (level.getServer().isSameThread() && BLOCK_ENTITY_TICK.hasStableModuleListeners(ScriptType.SERVER)) {
            BLOCK_ENTITY_TICK.postStableModuleNotification(
                    new BlockEntityTickEventJS.Actual(blockEntity, level, pos), blockEntity.getType(), ScriptType.SERVER);
        }
    }

    public static boolean postEndermanPlaced(Level level, BlockPos pos, BlockState state,
                                              BlockState supportState, Entity actor) {
        if (!hasPlacementAliasListeners()) return false;
        return postPlaced(level, pos, state, supportState, actor)
                || postStablePlacement(new StableBlockPlacement.Attempt(level, pos, null, state, Direction.UP, actor));
    }

    private static final class StablePlacement extends BlockPlacedEventJS {
        private StablePlacement(Level level, BlockPos pos, BlockState state, BlockState against, Entity actor) {
            super(level, pos, state, against, actor);
        }
    }

    public static boolean hasPlacementAliasListeners() {
        return ENTITY_PLACED.hasListeners() || PLACED.hasListeners();
    }

    public static boolean hasStablePlacementListeners() {
        return ENTITY_MULTI_PLACED.hasListeners() || hasPlacementAliasListeners();
    }

    public static boolean postStablePlacement(StableBlockPlacement.Attempt attempt) {
        var level = attempt.level();
        if (!ServerLevelEventThread.isCurrent(level)) return false;
        var pos = attempt.pos();
        var state = attempt.state();
        var actor = attempt.actor();
        var block = state.getBlock();
        if (attempt.secondary() != null && ENTITY_MULTI_PLACED.hasListeners() && ENTITY_MULTI_PLACED.post(
                FabricStableEventProjections.blockSnapshot(level, pos, block, actor), block)) return true;
        if (!hasPlacementAliasListeners()) return false;
        var against = level.getBlockState(pos.relative(attempt.face().getOpposite()));
        var event = new StablePlacement(level, pos, state, against, actor);
        return ENTITY_PLACED.postStableModuleCancellable(event, block, ScriptType.SERVER)
                || PLACED.postStableModuleCancellable(event, block, ScriptType.SERVER);
    }

    public static boolean postEntityMultiPlaced(Level level, BlockPos pos, BlockState state, Entity actor) {
        return postPlaced(level, pos, state, level.getBlockState(pos.below()), actor);
    }

    /**
     * A tool (axe, shovel, hoe) is about to modify a block state:
     * {@code MixinToolBlockModification} calls this at the HEAD of {@code useOn}.
     *
     * @return true if a listener cancelled the tool modification, in which case the mixin returns
     *         {@code InteractionResult.PASS}
     */
    public static boolean postToolModification(Level level, BlockPos pos, BlockState state,
                                               Player player) {
        if (!ServerLevelEventThread.isCurrent(level) || !TOOL_MODIFICATION.hasListeners()) {
            return false;
        }
        return TOOL_MODIFICATION.post(
                FabricStableEventProjections.blockSnapshot(level, pos, state.getBlock(), player),
                state.getBlock());
    }
}
