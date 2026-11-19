package com.tkisor.nekojs.fabric.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.tkisor.nekojs.fabric.event.FabricPlayerEventBindings;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Player.class)
public abstract class MixinCombatItemDestruction {
    @WrapMethod(method = "itemAttackInteraction(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/damagesource/DamageSource;Z)V")
    private void nekojs$captureAttackTool(Entity target, ItemStack stack, DamageSource damageSource, boolean applyToTarget,
                                          Operation<Void> original) {
        if ((Object) this instanceof ServerPlayer player) {
            FabricPlayerEventBindings.captureAttackDestruction(player, stack,
                    () -> original.call(target, stack, damageSource, applyToTarget));
        } else {
            original.call(target, stack, damageSource, applyToTarget);
        }
    }

    @WrapOperation(method = "itemAttackInteraction(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/damagesource/DamageSource;Z)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;isEmpty()Z", ordinal = 1),
            require = 1, allow = 1)
    private boolean nekojs$checkExhaustedWeapon(ItemStack stack, Operation<Boolean> original) {
        boolean exhausted = original.call(stack);
        if (exhausted && (Object) this instanceof ServerPlayer player) {
            FabricPlayerEventBindings.postAttackDestruction(player, stack);
        }
        return exhausted;
    }
}
