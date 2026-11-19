package com.tkisor.nekojs.mixin;

import com.tkisor.nekojs.bindings.event.NeoForgeCommandRegistration;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerList.class)
public abstract class NeoForgeInstalledCommandsMixin {
    @Inject(method = "reloadResources", at = @At("HEAD"))
    private void nekojs$postInstalledCommands(CallbackInfo ci) {
        NeoForgeCommandRegistration.postInstalled(((PlayerList) (Object) this).getServer());
    }
}
