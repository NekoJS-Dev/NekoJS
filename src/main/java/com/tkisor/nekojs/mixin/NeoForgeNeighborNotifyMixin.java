package com.tkisor.nekojs.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Cancellable;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
public abstract class NeoForgeNeighborNotifyMixin {
    @ModifyExpressionValue(
            method = "updateNeighborsAt(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/Block;)V",
            at = @At(value = "INVOKE", remap = false,
                    target = "Lnet/neoforged/neoforge/event/level/BlockEvent$NeighborNotifyEvent;isCanceled()Z")
    )
    private boolean nekojs$honorNeighborNotifyCancellation(boolean canceled, @Cancellable CallbackInfo ci) {
        if (canceled) {
            ci.cancel();
        }
        return canceled;
    }
}
