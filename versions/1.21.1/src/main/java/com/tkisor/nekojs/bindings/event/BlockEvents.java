package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.event.DispatchKey;
import com.tkisor.nekojs.api.event.EventBusForgeBridge;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.api.event.StableBlockEvent;
import com.tkisor.nekojs.event.level.BlockEntityTickEvent;
import com.tkisor.nekojs.event.level.RandomTickEvent;
import com.tkisor.nekojs.wrapper.event.block.BlockBrokenEventJS;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.BlockEvent.BreakEvent;

import java.util.List;
import java.util.function.Function;

/** Declares the complete NeoForge block event group and its native bridge. */
public final class BlockEvents {
    private BlockEvents() {}

    /** Event group published as {@code BlockEvents} in the versioned and stable event modules. */
    public static final EventGroup GROUP = EventGroup.of("BlockEvents");

    public static final EventBusJS<BlockBrokenEventJS, Block> BROKEN =
            GROUP.add("broken", ScriptType.SERVER, EventBusJS.of(
                    BlockBrokenEventJS.class, true, dispatchByBlock(BlockBrokenEventJS::getBlock)), api -> {
                api.versioned();
                api.stable()
                        .when(event -> event.getLevel() instanceof ServerLevel level && level.getServer().isSameThread())
                        .view(StableBlockEvent.class, NeoForgeStableEventProjections::blockBroken)
                        .selector(String.class, NeoForgeStableEventProjections::resolveBlockSelector)
                        .cancellable()
                        .scriptTypes(ScriptType.SERVER);
            });

    public static final EventBusJS<BlockEvent.EntityPlaceEvent, Block> ENTITY_PLACED =
            GROUP.server("entityPlaced", BlockEvent.EntityPlaceEvent.class, dispatchByBlock(), api -> {
                api.versioned().when(BlockEvents::isVersionedPlacement);
                api.stable()
                        .when(BlockEvents::isStablePlacement)
                        .view(StableBlockEvent.class, NeoForgeStableEventProjections::blockEvent)
                        .selector(String.class, NeoForgeStableEventProjections::resolveBlockSelector)
                        .cancellable()
                        .scriptTypes(ScriptType.SERVER);
            });
    public static final EventBusJS<BlockEvent.EntityMultiPlaceEvent, Block> ENTITY_MULTI_PLACED =
            GROUP.server("entityMultiPlaced", BlockEvent.EntityMultiPlaceEvent.class, dispatchByBlock(), api -> {
                api.versioned().when(BlockEvents::isVersionedPlacement);
                api.stable()
                        .when(event -> event instanceof StableMultiPlacement)
                        .view(StableBlockEvent.class, NeoForgeStableEventProjections::blockEvent)
                        .selector(String.class, NeoForgeStableEventProjections::resolveBlockSelector)
                        .cancellable()
                        .scriptTypes(ScriptType.SERVER);
            });
    public static final EventBusJS<BlockEvent.NeighborNotifyEvent, Block> NEIGHBOR_NOTIFY =
            GROUP.server("neighborNotify", BlockEvent.NeighborNotifyEvent.class, dispatchByBlock(), api -> {
                api.versioned();
                api.stable()
                        .when(event -> ServerLevelEventThread.isCurrent(event.getLevel()))
                        .view(StableBlockEvent.class, NeoForgeStableEventProjections::blockEvent)
                        .selector(String.class, NeoForgeStableEventProjections::resolveBlockSelector)
                        .cancellable()
                        .scriptTypes(ScriptType.SERVER);
            });
    public static final EventBusJS<BlockEvent.FluidPlaceBlockEvent, Block> FLUID_PLACED =
            GROUP.server("fluidPlaced", BlockEvent.FluidPlaceBlockEvent.class, dispatchByBlock(), api -> {
                api.versioned().when(event -> !(event instanceof NeoForgeSupplementalFluidPlacement));
                api.stable()
                        .when(event -> ServerLevelEventThread.isCurrent(event.getLevel()))
                        .view(StableBlockEvent.class, NeoForgeStableEventProjections::blockEvent)
                        .selector(String.class, NeoForgeStableEventProjections::resolveBlockSelector)
                        .cancellable()
                        .scriptTypes(ScriptType.SERVER);
            });
    public static final EventBusJS<BlockEvent.FarmlandTrampleEvent, Block> FARMLAND_TRAMPLE =
            GROUP.server("farmlandTrample", BlockEvent.FarmlandTrampleEvent.class, dispatchByBlock(), api -> {
                api.versioned();
                api.stable()
                        .when(event -> ServerLevelEventThread.isCurrent(event.getLevel()))
                        .view(StableBlockEvent.class, NeoForgeStableEventProjections::blockEvent)
                        .selector(String.class, NeoForgeStableEventProjections::resolveBlockSelector)
                        .cancellable()
                        .scriptTypes(ScriptType.SERVER);
            });
    public static final EventBusJS<BlockEvent.PortalSpawnEvent, Block> PORTAL_SPAWN =
            GROUP.server("portalSpawn", BlockEvent.PortalSpawnEvent.class, dispatchByBlock(), api -> {
                api.versioned();
                api.stable()
                        .when(event -> ServerLevelEventThread.isCurrent(event.getLevel()))
                        .view(StableBlockEvent.class, NeoForgeStableEventProjections::blockEvent)
                        .selector(String.class, NeoForgeStableEventProjections::resolveBlockSelector)
                        .cancellable()
                        .scriptTypes(ScriptType.SERVER);
            });
    public static final EventBusJS<BlockEvent.BlockToolModificationEvent, Block> TOOL_MODIFICATION =
            GROUP.server("toolModification", BlockEvent.BlockToolModificationEvent.class, dispatchByBlock(), api -> {
                api.versioned();
                api.stable()
                        .view(StableBlockEvent.class, NeoForgeStableEventProjections::blockEvent)
                        .when(NeoForgeToolModificationEligibility::isEligible)
                        .selector(String.class, NeoForgeStableEventProjections::resolveBlockSelector)
                        .cancellable()
                        .scriptTypes(ScriptType.SERVER);
            });

    public static final EventBusJS<PlayerInteractEvent.RightClickBlock, Block> RIGHT_CLICKED =
            GROUP.server("rightClicked", PlayerInteractEvent.RightClickBlock.class,
                    dispatchByBlock(e -> e.getLevel().getBlockState(e.getPos()).getBlock()), api -> {
                        api.versioned();
                        api.stable()
                                .when(event -> ServerLevelEventThread.isCurrent(event.getLevel()))
                                .view(StableBlockEvent.class, NeoForgeStableEventProjections::blockInteract)
                                .selector(String.class, NeoForgeStableEventProjections::resolveBlockSelector)
                                .cancellable()
                                .scriptTypes(ScriptType.SERVER);
                    });
    public static final EventBusJS<BlockEvent.EntityPlaceEvent, Block> PLACED =
            GROUP.server("placed", BlockEvent.EntityPlaceEvent.class, dispatchByBlock(), api -> {
                api.versioned().when(BlockEvents::isVersionedPlacement);
                api.stable()
                        .when(BlockEvents::isStablePlacement)
                        .view(StableBlockEvent.class, NeoForgeStableEventProjections::blockEvent)
                        .selector(String.class, NeoForgeStableEventProjections::resolveBlockSelector)
                        .cancellable()
                        .scriptTypes(ScriptType.SERVER);
            });
    public static final EventBusJS<PlayerInteractEvent.LeftClickBlock, Block> LEFT_CLICKED =
            GROUP.server("leftClicked", PlayerInteractEvent.LeftClickBlock.class,
                    dispatchByBlock(e -> e.getLevel().getBlockState(e.getPos()).getBlock()), api -> {
                        api.versioned();
                        api.stable()
                                .when(event -> event.getAction() == PlayerInteractEvent.LeftClickBlock.Action.START
                                        && ServerLevelEventThread.isCurrent(event.getLevel()))
                                .view(StableBlockEvent.class, NeoForgeStableEventProjections::blockInteract)
                                .selector(String.class, NeoForgeStableEventProjections::resolveBlockSelector)
                                .cancellable()
                                .scriptTypes(ScriptType.SERVER);
                    });

    /** Receives the mixin-posted native random tick event. */
    public static final EventBusJS<RandomTickEvent, Block> RANDOM_TICK =
            GROUP.server("randomTick", RandomTickEvent.class,
                    dispatchByBlock(RandomTickEvent::getBlock), api -> {
                        api.versioned();
                        api.stable()
                                .when(event -> event.getLevel() != null && event.getLevel().getServer().isSameThread())
                                .view(StableBlockEvent.class, NeoForgeStableEventProjections::randomTick)
                                .selector(String.class, NeoForgeStableEventProjections::resolveBlockSelector)
                                .scriptTypes(ScriptType.SERVER);
                    });

    /** Receives the mixin-posted native block entity tick event. */
    public static final EventBusJS<BlockEntityTickEvent, BlockEntityType<?>> BLOCK_ENTITY_TICK =
            GROUP.server("blockEntityTick", BlockEntityTickEvent.class,
                    DispatchKey.of(BlockEntityType.class, BlockEntityTickEvent::getType), api -> {
                        api.versioned().when(event -> !(event instanceof BlockEntityTickEvent.Actual));
                        api.stable()
                                .when(event -> event instanceof BlockEntityTickEvent.Actual)
                                .view(StableBlockEvent.class, NeoForgeStableEventProjections::blockEntityTick)
                                .selector(String.class, NeoForgeStableEventProjections::resolveBlockEntityTypeSelector)
                                .scriptTypes(ScriptType.SERVER);
                    });

    public static boolean hasStablePlacementListeners() {
        return ENTITY_PLACED.hasListeners() || PLACED.hasListeners() || ENTITY_MULTI_PLACED.hasListeners();
    }

    public static boolean postStablePlacement(StableBlockPlacement.Attempt attempt) {
        var level = attempt.level();
        if (!ServerLevelEventThread.isCurrent(level)) return false;
        var pos = attempt.pos();
        var primary = BlockSnapshot.create(level.dimension(), level, pos);
        var against = level.getBlockState(pos.relative(attempt.face().getOpposite()));
        if (attempt.secondary() != null) {
            var secondary = BlockSnapshot.create(level.dimension(), level, attempt.secondary());
            var event = new StableMultiPlacement(List.of(primary, secondary), against, attempt.state(), attempt.actor());
            return ENTITY_MULTI_PLACED.post(event) || ENTITY_PLACED.post(event) || PLACED.post(event);
        }
        var event = new StableSinglePlacement(primary, against, attempt.state(), attempt.actor());
        return ENTITY_PLACED.post(event) || PLACED.post(event);
    }

    private static boolean isStablePlacement(BlockEvent.EntityPlaceEvent event) {
        return !isVersionedPlacement(event);
    }

    private static boolean isVersionedPlacement(BlockEvent.EntityPlaceEvent event) {
        return !(event instanceof StableSinglePlacement || event instanceof StableMultiPlacement);
    }

    private static final class StableSinglePlacement extends BlockEvent.EntityPlaceEvent {
        private final BlockState state;

        private StableSinglePlacement(BlockSnapshot snapshot, BlockState against, BlockState state, Entity actor) {
            super(snapshot, against, actor);
            this.state = state;
        }

        @Override
        public BlockState getState() {
            return state;
        }

        @Override
        public BlockState getPlacedBlock() {
            return state;
        }
    }

    private static final class StableMultiPlacement extends BlockEvent.EntityMultiPlaceEvent {
        private final BlockState state;

        private StableMultiPlacement(List<BlockSnapshot> snapshots, BlockState against, BlockState state, Entity actor) {
            super(snapshots, against, actor);
            this.state = state;
        }

        @Override
        public BlockState getState() {
            return state;
        }

        @Override
        public BlockState getPlacedBlock() {
            return state;
        }
    }

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
        .bind(ENTITY_PLACED, e -> !e.getLevel().isClientSide())
        .bind(ENTITY_MULTI_PLACED, e -> !e.getLevel().isClientSide())
        .bind(NEIGHBOR_NOTIFY, e -> !e.getLevel().isClientSide())
        .bind(FARMLAND_TRAMPLE, e -> !e.getLevel().isClientSide())
        .bind(PORTAL_SPAWN, e -> !e.getLevel().isClientSide())
        .bind(TOOL_MODIFICATION, e -> !e.getLevel().isClientSide())
        // Interaction callbacks also fire on the client; these buses accept server instances only.
        .bind(RIGHT_CLICKED, e -> !e.getLevel().isClientSide())
        .bind(PLACED, e -> !e.getLevel().isClientSide())
        .bind(LEFT_CLICKED, e -> !e.getLevel().isClientSide())
        .bind(RANDOM_TICK)
        .bind(BLOCK_ENTITY_TICK, e -> e.getBlockEntity() != null
                && e.getBlockEntity().getLevel() != null
                && !e.getBlockEntity().getLevel().isClientSide());

    static {
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, BlockEvent.FluidPlaceBlockEvent.class,
                NeoForgeSupplementalFluidPlacement::onNative);
    }

}
