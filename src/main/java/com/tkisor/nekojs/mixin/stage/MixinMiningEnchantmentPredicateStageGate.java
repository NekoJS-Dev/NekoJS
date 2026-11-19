package com.tkisor.nekojs.mixin.stage;

import com.tkisor.nekojs.stage.StageEnchantmentUse;
import net.minecraft.core.component.predicates.EnchantmentsPredicate;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(EnchantmentsPredicate.class)
public abstract class MixinMiningEnchantmentPredicateStageGate {
    @ModifyVariable(
            method = "matches(Lnet/minecraft/world/item/enchantment/ItemEnchantments;)Z",
            at = @At("HEAD"), argsOnly = true
    )
    private ItemEnchantments nekojs$filterMiningEnchantments(ItemEnchantments enchantments) {
        return StageEnchantmentUse.miningEnchantments(enchantments);
    }
}
