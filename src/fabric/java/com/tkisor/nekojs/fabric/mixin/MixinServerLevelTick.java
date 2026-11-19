package com.tkisor.nekojs.fabric.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.tkisor.nekojs.fabric.event.FabricLevelEventBindings;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftServer.class)
public abstract class MixinServerLevelTick {
    @Inject(method = "tickChildren(Ljava/util/function/BooleanSupplier;)V",
            at = @At(value = "INVOKE_STRING", target = "Lnet/minecraft/util/profiling/ProfilerFiller;push(Ljava/lang/String;)V",
                    args = "ldc=tick", shift = At.Shift.AFTER), require = 1, allow = 1)
    private void nekojs$beforeLevelTick(CallbackInfo ci, @Local ServerLevel level) {
        FabricLevelEventBindings.postStableTick(level, true);
    }

    @Inject(method = "tickChildren(Ljava/util/function/BooleanSupplier;)V",
            slice = @Slice(from = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;tick(Ljava/util/function/BooleanSupplier;)V")),
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/profiling/ProfilerFiller;pop()V", ordinal = 0),
            require = 1, allow = 1)
    private void nekojs$afterLevelTick(CallbackInfo ci, @Local ServerLevel level) {
        FabricLevelEventBindings.postStableTick(level, false);
    }
}
