package com.tkisor.nekojs.fabric.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import com.tkisor.nekojs.fabric.event.FabricItemEventBindings;
import com.tkisor.nekojs.fabric.event.FabricItemEventBindingsV2;
import com.tkisor.nekojs.fabric.event.FabricPlayerEventBindings;
import com.tkisor.nekojs.fabric.event.FabricServerEventBindings;
import com.tkisor.nekojs.stage.StageGateEnforcer;
import net.minecraft.network.chat.ChatDecorator;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

/**
 * Hook for the serverbound UseItem packet: enforces item-use stage gates and feeds the Fabric
 * server-side {@code ItemEvents.rightClicked} bus. When a stage gate denies use or a script
 * listener cancels (returns {@code true}), vanilla use handling is skipped.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerMixin {

    @Shadow public ServerPlayer player;

    @WrapOperation(method = "lambda$handleChat$0(Lnet/minecraft/network/protocol/game/ServerboundChatPacket;Ljava/util/Optional;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/network/chat/ChatDecorator;decorate(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/network/chat/Component;)Lnet/minecraft/network/chat/Component;"),
            require = 1, allow = 1)
    private Component nekojs$dispatchStableChat(ChatDecorator decorator, ServerPlayer sender, Component message,
                                               Operation<Component> original,
                                               @Share("stableChatCancelled") LocalBooleanRef cancelled) {
        String rawText = message.getContents() instanceof PlainTextContents plain ? plain.text() : "";
        boolean denied = FabricServerEventBindings.postStableChat(sender, rawText);
        cancelled.set(denied);
        return denied ? message : original.call(decorator, sender, message);
    }

    @ModifyArg(method = "lambda$handleChat$0(Lnet/minecraft/network/protocol/game/ServerboundChatPacket;Ljava/util/Optional;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/FutureChain;append(Ljava/util/concurrent/CompletableFuture;Ljava/util/function/Consumer;)V"),
            index = 1, require = 1, allow = 1)
    private Consumer<?> nekojs$skipCancelledChatBroadcast(Consumer<?> continuation,
                                                         @Share("stableChatCancelled") LocalBooleanRef cancelled) {
        return cancelled.get() ? ignored -> {} : continuation;
    }

    @Inject(method = "handleInteract", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;interactOn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/InteractionResult;"),
            cancellable = true, require = 1, allow = 1)
    private void nekojs$onHandleInteract(ServerboundInteractPacket packet, CallbackInfo ci,
                                         @Local Entity target, @Local InteractionHand hand) {
        if (!StageGateEnforcer.allowInteractEntity(player, target, player.getItemInHand(hand))
                || FabricPlayerEventBindings.postEntityInteract(player, target, hand)
                || FabricItemEventBindingsV2.postEntityInteracted(player, target, player.getItemInHand(hand), hand)) {
            ci.cancel();
        }
    }

    @Inject(method = "handleUseItem(Lnet/minecraft/network/protocol/game/ServerboundUseItemPacket;)V",
            at = @At(value = "INVOKE", shift = At.Shift.AFTER,
                    target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V"),
            cancellable = true, require = 1, allow = 1)
    private void nekojs$postVersionedUseItem(ServerboundUseItemPacket packet, CallbackInfo ci) {
        if (FabricItemEventBindings.postRightClicked(player, packet.getHand())) ci.cancel();
    }

    @Inject(method = "handleUseItem(Lnet/minecraft/network/protocol/game/ServerboundUseItemPacket;)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayerGameMode;useItem(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResult;"),
            cancellable = true, require = 1, allow = 1)
    private void nekojs$onHandleUseItem(ServerboundUseItemPacket packet, CallbackInfo ci) {
        ItemStack stack = player.getItemInHand(packet.getHand());
        if (!StageGateEnforcer.allowUseItem(player, stack)) {
            player.inventoryMenu.broadcastChanges();
            if (player.containerMenu != null && player.containerMenu != player.inventoryMenu) {
                player.containerMenu.broadcastChanges();
            }
            ci.cancel();
        }
    }
}
