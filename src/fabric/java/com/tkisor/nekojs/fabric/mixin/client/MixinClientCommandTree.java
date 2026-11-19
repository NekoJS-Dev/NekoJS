package com.tkisor.nekojs.fabric.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.tkisor.nekojs.client.ClientEventThread;
import com.tkisor.nekojs.fabric.event.FabricClientEventBindings;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundCommandsPacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ClientPacketListener.class)
public abstract class MixinClientCommandTree {
    @WrapMethod(method = "handleLogin(Lnet/minecraft/network/protocol/game/ClientboundLoginPacket;)V")
    private void nekojs$onLoginCommands(ClientboundLoginPacket packet, Operation<Void> original) {
        if (!ClientEventThread.isCurrent()) {
            original.call(packet);
            return;
        }
        var previous = ClientCommands.getActiveDispatcher();
        original.call(packet);
        if (ClientCommands.getActiveDispatcher() != previous) {
            FabricClientEventBindings.postClientCommandTree((ClientPacketListener) (Object) this);
        }
    }

    @WrapMethod(method = "handleCommands(Lnet/minecraft/network/protocol/game/ClientboundCommandsPacket;)V")
    private void nekojs$onCommandTree(ClientboundCommandsPacket packet, Operation<Void> original) {
        boolean onOwner = ClientEventThread.isCurrent();
        original.call(packet);
        if (onOwner) FabricClientEventBindings.postClientCommandTree((ClientPacketListener) (Object) this);
    }
}
