package com.tkisor.nekojs.fabric.mixin.client;

import com.llamalad7.mixinextras.sugar.Local;
import com.tkisor.nekojs.fabric.event.FabricClientEventBindings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fabric client hook for {@code ClientEvents.cloned} after {@code ClientPacketListener#handleRespawn}
 * installs the respawned local player entity.
 */
@Mixin(ClientPacketListener.class)
public abstract class MixinClientPacketListenerRespawn {

    @Inject(method = "handleLogin(Lnet/minecraft/network/protocol/game/ClientboundLoginPacket;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;setId(I)V"))
    private void nekojs$onHandleLogin(ClientboundLoginPacket packet, CallbackInfo ci) {
        FabricClientEventBindings.postStableLogin(Minecraft.getInstance().player);
    }

    @Inject(method = "handleRespawn(Lnet/minecraft/network/protocol/game/ClientboundRespawnPacket;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;addEntity(Lnet/minecraft/world/entity/Entity;)V"))
    private void nekojs$onHandleRespawn(ClientboundRespawnPacket packet, CallbackInfo ci,
                                       @Local(index = 5) LocalPlayer originalPlayer) {
        FabricClientEventBindings.postClientCloned(Minecraft.getInstance().player, originalPlayer);
    }
}
