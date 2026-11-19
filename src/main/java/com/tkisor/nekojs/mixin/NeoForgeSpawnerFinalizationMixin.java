package com.tkisor.nekojs.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.tkisor.nekojs.bindings.event.NeoForgeSpawnFinalizationRouting;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.EventHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = EventHooks.class, remap = false)
public abstract class NeoForgeSpawnerFinalizationMixin {
    @WrapOperation(method = "finalizeMobSpawnSpawner", at = @At(value = "INVOKE",
            target = "Lnet/neoforged/bus/api/IEventBus;post(Lnet/neoforged/bus/api/Event;)Lnet/neoforged/bus/api/Event;"),
            require = 1, allow = 1)
    private static Event nekojs$postSpawnerFinalization(IEventBus bus, Event event, Operation<Event> original,
                                                        @Local(argsOnly = true) boolean initializes) {
        return initializes ? original.call(bus, event)
                : NeoForgeSpawnFinalizationRouting.postWithoutInitialization(event, () -> original.call(bus, event));
    }
}
