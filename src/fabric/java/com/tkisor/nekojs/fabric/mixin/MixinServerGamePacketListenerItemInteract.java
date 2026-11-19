package com.tkisor.nekojs.fabric.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.tkisor.nekojs.bindings.event.ServerLevelEventThread;
import com.tkisor.nekojs.fabric.event.FabricItemEventBindingsV2;
import com.tkisor.nekojs.fabric.event.FabricPlayerEventBindings;
import com.tkisor.nekojs.stage.StageGateEnforcer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fabric hook for entity-interaction stage gate enforcement, {@code PlayerEvents.entityInteract},
 * and {@code ItemEvents.entityInteracted}: injects at the {@code ServerPlayer.interactOn} call
 * site in {@code ServerGamePacketListenerImpl#handleInteract(ServerboundInteractPacket)}.
 *
 * <p>Stage gate checks run first; if denied, or if a script listener returns {@code true},
 * {@code ci.cancel()} skips vanilla entity interaction handling.
 */
@Mixin(Player.class)
public abstract class MixinServerGamePacketListenerItemInteract {
    @Inject(method = "interactOn", at = @At("HEAD"), cancellable = true)
    private void nekojs$enforceEntityGate(Entity target, InteractionHand hand, Vec3 hit,
                                         CallbackInfoReturnable<InteractionResult> cir) {
        if ((Object) this instanceof ServerPlayer player
                && !StageGateEnforcer.allowInteractEntity(player, target, player.getItemInHand(hand))) {
            cir.setReturnValue(InteractionResult.FAIL);
        }
    }

    @Inject(method = "interactOn", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;getItemInHand(Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/item/ItemStack;", ordinal = 0),
            cancellable = true)
    private void nekojs$beforeEntityInteract(Entity target, InteractionHand hand, Vec3 hit,
                                             CallbackInfoReturnable<InteractionResult> cir) {
        if (!((Object) this instanceof ServerPlayer player) || !ServerLevelEventThread.isCurrent(player.level())) {
            return;
        }
        if (FabricPlayerEventBindings.postStableEntityInteract(player, target, hand)
                || FabricItemEventBindingsV2.postStableEntityInteracted(player, target, hand)) {
            cir.setReturnValue(InteractionResult.PASS);
        }
    }

    @WrapMethod(method = "interactOn")
    private InteractionResult nekojs$captureInteractionDestruction(Entity target, InteractionHand hand, Vec3 hit,
                                                                     Operation<InteractionResult> original) {
        if ((Object) this instanceof ServerPlayer player && !player.isSpectator() && !player.hasInfiniteMaterials()) {
            return FabricPlayerEventBindings.captureDestroyed(player, player.getItemInHand(hand),
                    () -> original.call(target, hand, hit), InteractionResult::consumesAction);
        }
        return original.call(target, hand, hit);
    }
}
