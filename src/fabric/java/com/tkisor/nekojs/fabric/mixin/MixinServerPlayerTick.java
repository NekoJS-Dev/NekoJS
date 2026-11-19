package com.tkisor.nekojs.fabric.mixin;

import com.tkisor.nekojs.fabric.event.FabricPlayerEventBindings;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public abstract class MixinServerPlayerTick {
    @Inject(method = "tick()V", at = @At("HEAD"))
    private void nekojs$beforePlayerTick(CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayer player) {
            FabricPlayerEventBindings.postTickPre(player);
        }
    }

    @Inject(method = "tick()V", at = @At("RETURN"))
    private void nekojs$afterPlayerTick(CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayer player) {
            FabricPlayerEventBindings.postTickPost(player);
        }
    }
}
