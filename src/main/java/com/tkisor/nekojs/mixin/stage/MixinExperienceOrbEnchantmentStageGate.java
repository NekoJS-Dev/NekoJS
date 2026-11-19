package com.tkisor.nekojs.mixin.stage;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.tkisor.nekojs.stage.StageEnchantmentUse;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ExperienceOrb.class)
public abstract class MixinExperienceOrbEnchantmentStageGate {
    @WrapOperation(method = "repairPlayerItems",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/enchantment/EnchantmentHelper;modifyDurabilityToRepairFromXp(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/item/ItemStack;I)I"))
    private int nekojs$bindRepairActor(ServerLevel level, ItemStack stack, int amount, Operation<Integer> original,
            ServerPlayer player) {
        return StageEnchantmentUse.withActor(player, stack, () -> original.call(level, stack, amount));
    }
}
