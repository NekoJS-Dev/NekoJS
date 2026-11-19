package com.tkisor.nekojs.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.tkisor.nekojs.bindings.event.NeoForgeSupplementalFluidPlacement;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.LavaFluid;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.level.BlockEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LavaFluid.class)
public abstract class NeoForgeVanillaFluidMixin {
    @WrapOperation(method = {"randomTick", "spreadTo"}, at = @At(value = "INVOKE", remap = false,
            target = "Lnet/neoforged/neoforge/event/EventHooks;fireFluidPlaceBlockEvent(Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Lnet/minecraft/world/level/block/state/BlockState;"),
            require = 3, allow = 3)
    private BlockState nekojs$scopeLavaPlacement(LevelAccessor level, BlockPos pos, BlockPos liquidPos, BlockState state,
                                                Operation<BlockState> original) {
        return NeoForgeSupplementalFluidPlacement.withVanillaPlacement(level, pos, liquidPos,
                () -> original.call(level, pos, liquidPos, state));
    }

    @Mixin(value = EventHooks.class, remap = false)
    public static abstract class EventDispatch {
        @WrapOperation(method = "fireFluidPlaceBlockEvent", at = @At(value = "INVOKE",
                target = "Lnet/neoforged/bus/api/IEventBus;post(Lnet/neoforged/bus/api/Event;)Lnet/neoforged/bus/api/Event;"),
                require = 1, allow = 1)
        private static Event nekojs$captureFluidPlacement(IEventBus bus, Event event, Operation<Event> original) {
            if (event instanceof BlockEvent.FluidPlaceBlockEvent fluidPlacement) {
                NeoForgeSupplementalFluidPlacement.captureNative(fluidPlacement);
            }
            return original.call(bus, event);
        }
    }
}
