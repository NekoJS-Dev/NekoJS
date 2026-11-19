package com.tkisor.nekojs.fabric.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.tkisor.nekojs.fabric.event.FabricEntityEventBindingsV2;
import com.tkisor.nekojs.fabric.event.FabricPlayerEventBindings;
import com.tkisor.nekojs.stage.StageGateEnforcer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fabric hooks for item-use stage gate enforcement and stable {@code EntityEvents} item-use
 * lifecycle buses ({@code useItemStarted}, {@code useItemStopped}, {@code useItemFinished},
 * {@code useItemTick}) on {@link LivingEntity}.
 */
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntityUseItem {
    @Shadow protected int useItemRemaining;

    @Shadow protected abstract void completeUsingItem();

    @Inject(method = "startUsingItem(Lnet/minecraft/world/InteractionHand;)V", at = @At("HEAD"), cancellable = true)
    private void nekojs$onUseItemStarted(InteractionHand hand, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self.level().isClientSide()) {
            return;
        }
        ItemStack stack = self.getItemInHand(hand);
        if (!stack.isEmpty() && self instanceof Player player && !StageGateEnforcer.allowUseItem(player, stack)) {
            ci.cancel();
            return;
        }
    }

    @Inject(method = "startUsingItem(Lnet/minecraft/world/InteractionHand;)V", at = @At(value = "FIELD",
            target = "Lnet/minecraft/world/entity/LivingEntity;useItem:Lnet/minecraft/world/item/ItemStack;",
            opcode = Opcodes.PUTFIELD), cancellable = true, require = 1, allow = 1)
    private void nekojs$beforeUseStateWrite(CallbackInfo ci, @Local ItemStack stack,
                                            @Share("stableUseDuration") LocalRef<Integer> duration) {
        LivingEntity entity = (LivingEntity) (Object) this;
        if (entity.level().isClientSide()) return;
        int ticks = stack.getUseDuration(entity);
        if (FabricEntityEventBindingsV2.postUseItemStarted(entity, stack, ticks) || ticks < 0) ci.cancel();
        else duration.set(ticks);
    }

    @WrapOperation(method = "startUsingItem(Lnet/minecraft/world/InteractionHand;)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;getUseDuration(Lnet/minecraft/world/entity/LivingEntity;)I"),
            require = 1, allow = 1)
    private int nekojs$useComputedDuration(ItemStack stack, LivingEntity entity, Operation<Integer> original,
                                           @Share("stableUseDuration") LocalRef<Integer> duration) {
        Integer ticks = duration.get();
        return ticks == null ? original.call(stack, entity) : ticks;
    }

    @WrapOperation(method = "releaseUsingItem()V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;releaseUsing(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;I)V"))
    private void nekojs$onUseItemStopped(ItemStack stack, Level level, LivingEntity entity, int remainingTicks,
                                         Operation<Void> original) {
        if (!level.isClientSide() && FabricEntityEventBindingsV2.postUseItemStopped(entity, stack, remainingTicks)) return;
        if (entity instanceof ServerPlayer player && !player.hasInfiniteMaterials()) {
            FabricPlayerEventBindings.captureDestroyed(player, stack, () -> {
                original.call(stack, level, entity, remainingTicks);
                return true;
            }, Boolean::booleanValue);
        } else {
            original.call(stack, level, entity, remainingTicks);
        }
    }

    @WrapOperation(method = "updateUsingItem(Lnet/minecraft/world/item/ItemStack;)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;onUseTick(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;I)V"))
    private void nekojs$useWhenTimeRemains(ItemStack stack, Level level, LivingEntity entity, int remainingTicks,
                                          Operation<Void> original) {
        if (remainingTicks > 0 || level.isClientSide()) original.call(stack, level, entity, remainingTicks);
    }

    @Inject(method = "updateUsingItem(Lnet/minecraft/world/item/ItemStack;)V", at = @At("HEAD"))
    private void nekojs$onUseItemTick(ItemStack stack, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!self.level().isClientSide()
                && FabricEntityEventBindingsV2.postUseItemTick(self, stack, self.getUseItemRemainingTicks())) {
            this.useItemRemaining = -1;
        }
    }

    @Inject(method = "updateUsingItem(Lnet/minecraft/world/item/ItemStack;)V", at = @At(value = "FIELD",
            target = "Lnet/minecraft/world/entity/LivingEntity;useItemRemaining:I", opcode = Opcodes.PUTFIELD,
            shift = At.Shift.AFTER), cancellable = true, require = 1, allow = 1)
    private void nekojs$finishNegativeUseTime(ItemStack stack, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (this.useItemRemaining < 0 && !self.level().isClientSide() && !stack.useOnRelease()) {
            completeUsingItem();
            ci.cancel();
        }
    }
}
