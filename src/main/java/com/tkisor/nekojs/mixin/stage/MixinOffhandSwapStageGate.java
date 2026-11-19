package com.tkisor.nekojs.mixin.stage;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Cancellable;
import com.tkisor.nekojs.stage.StageGateEnforcer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class MixinOffhandSwapStageGate {
    @WrapOperation(
            method = "handlePlayerAction",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerPlayer;setItemInHand(Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/item/ItemStack;)V",
                    ordinal = 0
            )
    )
    private void nekojs$checkWorldOffhandSwap(
            ServerPlayer player,
            InteractionHand hand,
            ItemStack stack,
            Operation<Void> original,
            @Cancellable CallbackInfo ci
    ) {
        if (hand == InteractionHand.OFF_HAND && !StageGateEnforcer.allowEquipItem(player, stack)) {
            player.inventoryMenu.sendAllDataToRemote();
            ci.cancel();
            return;
        }
        original.call(player, hand, stack);
    }
}
