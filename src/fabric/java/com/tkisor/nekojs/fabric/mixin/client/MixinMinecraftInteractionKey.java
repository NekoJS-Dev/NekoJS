package com.tkisor.nekojs.fabric.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Cancellable;
import com.tkisor.nekojs.fabric.event.FabricClientEventBindings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fabric client hook for {@code ClientEvents.interactionKey} at {@code Minecraft#startAttack},
 * {@code Minecraft#startUseItem}, and {@code Minecraft#pickBlock}.
 */
@Mixin(Minecraft.class)
public abstract class MixinMinecraftInteractionKey {

    @Shadow public LocalPlayer player;

    @Inject(method = "startAttack()Z", at = @At(value = "INVOKE", ordinal = 0,
            target = "Lnet/minecraft/world/item/ItemStack;get(Lnet/minecraft/core/component/DataComponentType;)Ljava/lang/Object;"),
            cancellable = true)
    private void nekojs$onStartAttack(CallbackInfoReturnable<Boolean> cir) {
        if (FabricClientEventBindings.postInteractionKey(true, false, false, InteractionHand.MAIN_HAND)) {
            player.swing(InteractionHand.MAIN_HAND);
            cir.setReturnValue(false);
        }
    }

    @WrapOperation(method = "continueAttack(Z)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;continueDestroyBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)Z"))
    private boolean nekojs$onContinueAttack(
            MultiPlayerGameMode gameMode, BlockPos pos, Direction direction, Operation<Boolean> original) {
        return FabricClientEventBindings.postInteractionKey(true, false, false, InteractionHand.MAIN_HAND)
                || original.call(gameMode, pos, direction);
    }

    @WrapOperation(method = "startUseItem()V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;getItemInHand(Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack nekojs$onUseHand(
            LocalPlayer player, InteractionHand hand, Operation<ItemStack> original, @Cancellable CallbackInfo ci) {
        if (FabricClientEventBindings.postInteractionKey(false, true, false, hand)) {
            player.swing(hand);
            ci.cancel();
            return ItemStack.EMPTY;
        }
        return original.call(player, hand);
    }

    @Inject(method = "pickBlockOrEntity()V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/Minecraft;hasControlDown()Z"), cancellable = true)
    private void nekojs$onPickBlock(CallbackInfo ci) {
        if (FabricClientEventBindings.postInteractionKey(false, false, true, InteractionHand.MAIN_HAND)) {
            ci.cancel();
        }
    }
}
