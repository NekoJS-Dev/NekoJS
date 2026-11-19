package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.api.ScriptType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.function.Supplier;

public final class NeoForgeSupplementalFluidPlacement extends BlockEvent.FluidPlaceBlockEvent {
    private static final ThreadLocal<NativePlacement> PLACEMENT = new ThreadLocal<>();

    private NeoForgeSupplementalFluidPlacement(Level level, BlockPos pos, BlockState state) {
        super(level, pos, pos, state);
    }

    public static BlockState stateForPlacement(Level level, BlockPos pos, BlockState state) {
        if (!ServerLevelEventThread.isCurrent(level) || !BlockEvents.FLUID_PLACED.hasListeners()) {
            return state;
        }
        var event = new NeoForgeSupplementalFluidPlacement(level, pos, state);
        return BlockEvents.FLUID_PLACED.post(event) ? event.getOriginalState() : state;
    }

    public static void withVanillaHardening(Level level, BlockPos pos, Runnable place) {
        withVanillaPlacement(level, pos, pos, () -> {
            place.run();
            return null;
        });
    }

    public static <T> T withVanillaPlacement(LevelAccessor level, BlockPos pos, BlockPos liquidPos, Supplier<T> place) {
        if (!ServerLevelEventThread.isCurrent(level) || !BlockEvents.FLUID_PLACED.hasStableModuleListeners(ScriptType.SERVER)) {
            return place.get();
        }
        var previous = PLACEMENT.get();
        PLACEMENT.set(new NativePlacement(level, pos.immutable(), liquidPos.immutable()));
        try {
            return place.get();
        } finally {
            if (previous == null) PLACEMENT.remove();
            else PLACEMENT.set(previous);
        }
    }

    public static void captureNative(BlockEvent.FluidPlaceBlockEvent event) {
        var scope = PLACEMENT.get();
        if (scope != null && scope.event == null && event.getLevel() == scope.level
                && scope.pos.equals(event.getPos()) && scope.liquidPos.equals(event.getLiquidPos())) {
            scope.event = event;
        }
    }

    static boolean isStable(BlockEvent.FluidPlaceBlockEvent event) {
        if (!ServerLevelEventThread.isCurrent(event.getLevel())) return false;
        if (event instanceof NeoForgeSupplementalFluidPlacement) return true;
        var scope = PLACEMENT.get();
        return scope != null && scope.event == event;
    }

    private static final class NativePlacement {
        private final LevelAccessor level;
        private final BlockPos pos;
        private final BlockPos liquidPos;
        private BlockEvent.FluidPlaceBlockEvent event;

        private NativePlacement(LevelAccessor level, BlockPos pos, BlockPos liquidPos) {
            this.level = level;
            this.pos = pos;
            this.liquidPos = liquidPos;
        }
    }

    static void onNative(BlockEvent.FluidPlaceBlockEvent event) {
        if (!event.getLevel().isClientSide() && BlockEvents.FLUID_PLACED.post(event)) {
            event.setNewState(event.getOriginalState());
            event.setCanceled(true);
        }
    }
}
