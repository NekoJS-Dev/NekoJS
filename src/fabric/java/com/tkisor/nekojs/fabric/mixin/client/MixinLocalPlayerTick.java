package com.tkisor.nekojs.fabric.mixin.client;

import com.tkisor.nekojs.fabric.event.FabricClientEventBindings;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fabric client hook for {@code ClientEvents.playerTickPre} and {@code ClientEvents.playerTickPost}
 * at the HEAD and TAIL of {@code LocalPlayer#tick}.
 */
@Mixin(Player.class)
public abstract class MixinLocalPlayerTick {

    @Inject(method = "tick()V", at = @At("HEAD"))
    private void nekojs$onTickPre(CallbackInfo ci) {
        var player = (Player) (Object) this;
        if (player.level().isClientSide()) {
            FabricClientEventBindings.postPlayerTickPre(player);
        }
    }

    @Inject(method = "tick()V", at = @At("TAIL"))
    private void nekojs$onTickPost(CallbackInfo ci) {
        var player = (Player) (Object) this;
        if (player.level().isClientSide()) {
            FabricClientEventBindings.postPlayerTickPost(player);
        }
    }
}
