package com.tkisor.nekojs.mixin;

import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import com.tkisor.nekojs.bindings.event.NeoForgeContainerEvents;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public abstract class NeoForgeNautilusContainerMixin {
    @Inject(method = "openNautilusInventory", at = @At(value = "INVOKE", remap = false,
            target = "Lnet/neoforged/bus/api/IEventBus;post(Lnet/neoforged/bus/api/Event;)Lnet/neoforged/bus/api/Event;"),
            require = 0, allow = 1)
    private void nekojs$useNativeContainerOpen(CallbackInfo ci, @Share("nativeOpen") LocalBooleanRef nativeOpen) {
        nativeOpen.set(true);
    }

    @Inject(method = "openNautilusInventory", at = @At("TAIL"), require = 1, allow = 1)
    private void nekojs$openNautilus(CallbackInfo ci, @Share("nativeOpen") LocalBooleanRef nativeOpen) {
        if (!nativeOpen.get()) NeoForgeContainerEvents.openedNautilus((ServerPlayer) (Object) this);
    }
}
