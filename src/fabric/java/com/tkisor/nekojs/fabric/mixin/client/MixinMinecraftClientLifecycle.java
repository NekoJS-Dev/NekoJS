package com.tkisor.nekojs.fabric.mixin.client;

import com.tkisor.nekojs.fabric.event.FabricClientEventBindings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MixinMinecraftClientLifecycle {
    @Shadow public LocalPlayer player;

    @Shadow public abstract boolean isGameLoadFinished();

    @Inject(method = "tick()V", at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/Minecraft;clientTickCount:J", opcode = Opcodes.PUTFIELD,
            shift = At.Shift.AFTER))
    private void nekojs$postStableTickPre(CallbackInfo ci) {
        if (isGameLoadFinished()) {
            FabricClientEventBindings.postStableTickPre();
        }
    }

    @Inject(method = "tick()V", at = @At("RETURN"))
    private void nekojs$postStableTickPost(CallbackInfo ci) {
        if (isGameLoadFinished()) {
            FabricClientEventBindings.postStableTickPost();
        }
    }

    @Inject(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;ZZ)V", at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/Minecraft;gameMode:Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;",
            opcode = Opcodes.PUTFIELD))
    private void nekojs$postStableLogout(Screen screen, boolean keepResourcePacks, boolean joiningNewWorld,
                                         CallbackInfo ci) {
        FabricClientEventBindings.postStableLogout(player);
    }
}
