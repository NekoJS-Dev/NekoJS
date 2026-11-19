package com.tkisor.nekojs.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.brigadier.CommandDispatcher;
import com.tkisor.nekojs.bindings.event.client.NeoForgeClientCommandRouting;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.network.protocol.game.ClientboundCommandsPacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.ClientCommandHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ClientPacketListener.class)
public abstract class NeoForgeClientCommandLoginMixin {
    @WrapMethod(method = "handleLogin(Lnet/minecraft/network/protocol/game/ClientboundLoginPacket;)V")
    private void nekojs$captureLoginCommands(ClientboundLoginPacket packet, Operation<Void> original) {
        NeoForgeClientCommandRouting.duringPacket(() -> original.call(packet));
    }

    @WrapMethod(method = "handleCommands(Lnet/minecraft/network/protocol/game/ClientboundCommandsPacket;)V")
    private void nekojs$captureCommandUpdate(ClientboundCommandsPacket packet, Operation<Void> original) {
        NeoForgeClientCommandRouting.duringPacket(() -> original.call(packet));
    }

    @WrapOperation(method = "handleCommands(Lnet/minecraft/network/protocol/game/ClientboundCommandsPacket;)V",
            at = @At(value = "INVOKE", remap = false,
                    target = "Lnet/neoforged/neoforge/client/ClientCommandHandler;mergeServerCommands(Lcom/mojang/brigadier/CommandDispatcher;Lnet/minecraft/commands/CommandBuildContext;)Lcom/mojang/brigadier/CommandDispatcher;"),
            require = 1, allow = 1)
    private CommandDispatcher<ClientSuggestionProvider> nekojs$captureCommandUpdateMerge(
            CommandDispatcher<ClientSuggestionProvider> dispatcher, CommandBuildContext context,
            Operation<CommandDispatcher<ClientSuggestionProvider>> original) {
        return NeoForgeClientCommandRouting.duringMerge(context, () -> original.call(dispatcher, context));
    }

    @Mixin(value = ClientCommandHandler.class, remap = false)
    public static abstract class Registration {
        @WrapOperation(method = "handleClientPlayerLogin", at = @At(value = "INVOKE",
                target = "Lnet/neoforged/neoforge/client/ClientCommandHandler;mergeServerCommands(Lcom/mojang/brigadier/CommandDispatcher;Lnet/minecraft/commands/CommandBuildContext;)Lcom/mojang/brigadier/CommandDispatcher;"),
                require = 1, allow = 1)
        private static CommandDispatcher<ClientSuggestionProvider> nekojs$captureLoginMerge(
                CommandDispatcher<ClientSuggestionProvider> dispatcher, CommandBuildContext context,
                Operation<CommandDispatcher<ClientSuggestionProvider>> original) {
            return NeoForgeClientCommandRouting.duringMerge(context, () -> original.call(dispatcher, context));
        }

        @WrapOperation(method = "mergeServerCommands", at = @At(value = "INVOKE",
                target = "Lnet/neoforged/bus/api/IEventBus;post(Lnet/neoforged/bus/api/Event;)Lnet/neoforged/bus/api/Event;"),
                require = 1, allow = 1)
        private static Event nekojs$captureNativeRegistration(IEventBus bus, Event event, Operation<Event> original) {
            NeoForgeClientCommandRouting.captureRegistration(event);
            return original.call(bus, event);
        }
    }
}
