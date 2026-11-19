package com.tkisor.nekojs.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.tkisor.nekojs.bindings.event.NeoForgeLevelUnloadRouting;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.level.LevelEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(MinecraftServer.class)
public abstract class NeoForgeStableLevelUnloadMixin {
    @WrapMethod(method = "stopServer()V", require = 1)
    private void nekojs$scopeStableLevelUnload(Operation<Void> original) {
        NeoForgeLevelUnloadRouting.duringStop(() -> original.call());
    }

    @WrapOperation(method = "stopServer()V", at = @At(value = "INVOKE", remap = false,
            target = "Lnet/neoforged/bus/api/IEventBus;post(Lnet/neoforged/bus/api/Event;)Lnet/neoforged/bus/api/Event;"),
            require = 1, allow = 1)
    private Event nekojs$postNativeLevelUnload(IEventBus bus, Event event, Operation<Event> original) {
        if (event instanceof LevelEvent.Unload unload) {
            return NeoForgeLevelUnloadRouting.postNative(unload, () -> original.call(bus, event));
        }
        return original.call(bus, event);
    }

    @WrapOperation(method = "stopServer()V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;close()V"), require = 1, allow = 1)
    private void nekojs$postStableLevelUnload(ServerLevel level, Operation<Void> original) {
        NeoForgeLevelUnloadRouting.postBeforeClose(level);
        original.call(level);
    }
}
