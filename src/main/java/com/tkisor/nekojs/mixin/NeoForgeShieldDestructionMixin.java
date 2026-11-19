package com.tkisor.nekojs.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.tkisor.nekojs.bindings.event.NeoForgeShieldDestruction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlocksAttacks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.Consumer;

@Mixin(BlocksAttacks.class)
public abstract class NeoForgeShieldDestructionMixin {
    @WrapOperation(method = "hurtBlockingItem(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/InteractionHand;FI)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/item/ItemStack;hurtAndBreak(ILnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Consumer;)V"),
            require = 1, allow = 1)
    private void nekojs$captureShieldBreak(ItemStack stack, int amount, ServerLevel level, LivingEntity actor,
                                          Consumer<Item> onBreak, Operation<Void> original,
                                          @Local(argsOnly = true) InteractionHand hand) {
        original.call(stack, amount, level, actor, NeoForgeShieldDestruction.captureBreak(actor, stack, hand, onBreak));
    }
}
