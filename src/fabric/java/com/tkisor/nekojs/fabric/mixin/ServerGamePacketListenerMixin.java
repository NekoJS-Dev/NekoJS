package com.tkisor.nekojs.fabric.mixin;

import com.tkisor.nekojs.fabric.event.FabricItemEventBindings;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hook for the serverbound UseItem packet: the Fabric server-side entry for
 * {@code ItemEvents.rightClicked}. fabric-api's UseItemCallback is a client-only event and cannot
 * feed the SERVER bus; NeoForge's RightClickItem is dispatched on the server instance (the side
 * filter in EventBusForgeBridge), and this hook matches that. When a script cancels (returns
 * {@code true}), vanilla use handling is skipped.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerMixin {

    @Shadow public ServerPlayer player;

    @Inject(method = "handleUseItem", at = @At("HEAD"), cancellable = true)
    private void nekojs$onHandleUseItem(ServerboundUseItemPacket packet, CallbackInfo ci) {
        if (FabricItemEventBindings.postRightClicked(player, packet.getHand())) {
            ci.cancel();
        }
    }
}
