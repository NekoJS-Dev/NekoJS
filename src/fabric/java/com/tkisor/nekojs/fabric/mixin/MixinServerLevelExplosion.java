package com.tkisor.nekojs.fabric.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.tkisor.nekojs.fabric.event.FabricLevelEventBindingsV2;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ServerExplosion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
public abstract class MixinServerLevelExplosion {
    @Inject(method = "explode", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/ServerExplosion;explode()I"), cancellable = true, require = 1, allow = 1)
    private void nekojs$onExplosionStart(CallbackInfo ci, @Local ServerExplosion explosion) {
        if (FabricLevelEventBindingsV2.postStableExplosionStart(explosion)) {
            ci.cancel();
        }
    }
}
