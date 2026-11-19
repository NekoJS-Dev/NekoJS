package com.tkisor.nekojs.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.tkisor.nekojs.bindings.event.NeoForgePlayerChatScope;
import net.minecraft.network.chat.ChatDecorator;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class NeoForgePlayerChatMixin {
    @WrapOperation(method = {
            "lambda$handleChat$0(Lnet/minecraft/network/protocol/game/ServerboundChatPacket;Ljava/util/Optional;)V",
            "lambda$handleChat$6(Lnet/minecraft/network/protocol/game/ServerboundChatPacket;Ljava/util/Optional;)V"
    }, at = @At(value = "INVOKE", target = "Lnet/minecraft/network/chat/ChatDecorator;decorate(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/network/chat/Component;)Lnet/minecraft/network/chat/Component;"),
            require = 1, allow = 1)
    private Component nekojs$scopePacketChat(ChatDecorator decorator, ServerPlayer sender, Component message,
                                              Operation<Component> original) {
        return NeoForgePlayerChatScope.decorate(sender, message, () -> original.call(decorator, sender, message));
    }
}
