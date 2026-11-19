package com.tkisor.nekojs.mixin.stage;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.tkisor.nekojs.stage.StageDamageActors;
import com.tkisor.nekojs.stage.StageEnchantmentUse;
import com.tkisor.nekojs.stage.StageEquipmentEnchantments;
import com.tkisor.nekojs.stage.StageGateEnforcer;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.BiConsumer;

@Mixin(EnchantmentHelper.class)
public abstract class MixinEnchantmentHelperStageGate {
    @Unique
    private static final String NEKOJS_ITEM_ITERATION =
            "Lnet/minecraft/world/item/enchantment/EnchantmentHelper;runIterationOnItem(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/enchantment/EnchantmentHelper$EnchantmentVisitor;)V";
    @Unique
    private static final String NEKOJS_SLOT_ITERATION =
            "Lnet/minecraft/world/item/enchantment/EnchantmentHelper;runIterationOnItem(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/EquipmentSlot;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/enchantment/EnchantmentHelper$EnchantmentInSlotVisitor;)V";
    @Unique
    private static final String NEKOJS_EQUIPMENT_ITERATION =
            "Lnet/minecraft/world/item/enchantment/EnchantmentHelper;runIterationOnEquipment(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/enchantment/EnchantmentHelper$EnchantmentInSlotVisitor;)V";

    @WrapOperation(
            method = "runIterationOnItem(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/enchantment/EnchantmentHelper$EnchantmentVisitor;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/enchantment/EnchantmentHelper$EnchantmentVisitor;accept(Lnet/minecraft/core/Holder;I)V")
    )
    private static void nekojs$applyItemEffect(@Coerce Object visitor, Holder<Enchantment> enchantment, int level,
            Operation<Void> original, ItemStack stack) {
        if (!StageEquipmentEnchantments.visitModifier(visitor, enchantment, level,
                () -> original.call(visitor, enchantment, level))
                && nekojs$allows(StageEnchantmentUse.actorFor(stack), enchantment, level)) {
            original.call(visitor, enchantment, level);
        }
    }

    @WrapOperation(
            method = "forEachModifier(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/EquipmentSlot;Ljava/util/function/BiConsumer;)V",
            at = @At(value = "INVOKE", target = NEKOJS_ITEM_ITERATION)
    )
    private static void nekojs$bindAttributeVisitor(ItemStack stack, @Coerce Object visitor, Operation<Void> original,
            ItemStack item, EquipmentSlot slot, BiConsumer<Holder<Attribute>, AttributeModifier> consumer) {
        StageEquipmentEnchantments.withModifierVisitor(consumer, visitor, () -> original.call(stack, visitor));
    }

    @WrapOperation(
            method = "runIterationOnItem(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/EquipmentSlot;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/enchantment/EnchantmentHelper$EnchantmentInSlotVisitor;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/enchantment/EnchantmentHelper$EnchantmentInSlotVisitor;accept(Lnet/minecraft/core/Holder;ILnet/minecraft/world/item/enchantment/EnchantedItemInUse;)V")
    )
    private static void nekojs$applySlotEffect(@Coerce Object visitor, Holder<Enchantment> enchantment, int level,
            EnchantedItemInUse item, Operation<Void> original) {
        Boolean stopBlocked = StageEnchantmentUse.stopBlockedEffectsFor(visitor);
        if (stopBlocked == null || nekojs$allows(StageDamageActors.fromEntity(item.owner()), enchantment, level)) {
            original.call(visitor, enchantment, level, item);
        } else if (stopBlocked) {
            enchantment.value().stopLocationBasedEffects(level, item, item.owner());
        }
    }

    @WrapOperation(
            method = {"modifyDamage", "modifyFallBasedDamage", "modifyArmorEffectiveness", "modifyKnockback"},
            at = @At(value = "INVOKE", target = NEKOJS_ITEM_ITERATION)
    )
    private static void nekojs$bindDamageActor(ItemStack stack, @Coerce Object visitor, Operation<Void> original,
            ServerLevel level, ItemStack weapon, Entity victim, DamageSource source) {
        StageEnchantmentUse.withActor(StageDamageActors.fromDamageSource(source), stack, () -> original.call(stack, visitor));
    }

    @WrapOperation(
            method = {"processProjectileCount", "processProjectileSpread", "getFishingLuckBonus",
                    "getFishingTimeReduction", "getTridentReturnToOwnerAcceleration"},
            at = @At(value = "INVOKE", target = NEKOJS_ITEM_ITERATION)
    )
    private static void nekojs$bindItemActor(ItemStack stack, @Coerce Object visitor, Operation<Void> original,
            ServerLevel level, ItemStack used, Entity actor) {
        StageEnchantmentUse.withActor(actor, stack, () -> original.call(stack, visitor));
    }

    @WrapOperation(
            method = {"modifyCrossbowChargingTime", "getTridentSpinAttackStrength"},
            at = @At(value = "INVOKE", target = NEKOJS_ITEM_ITERATION)
    )
    private static void nekojs$bindChargeActor(ItemStack stack, @Coerce Object visitor, Operation<Void> original,
            ItemStack used, LivingEntity actor) {
        StageEnchantmentUse.withActor(actor, stack, () -> original.call(stack, visitor));
    }

    @WrapOperation(method = {"onProjectileSpawned", "onHitBlock"},
            at = @At(value = "INVOKE", target = NEKOJS_ITEM_ITERATION))
    private static void nekojs$bindProjectileActor(ItemStack stack, @Coerce Object visitor, Operation<Void> original,
            @Local EnchantedItemInUse item) {
        StageEnchantmentUse.withActor(item.owner(), stack, () -> original.call(stack, visitor));
    }

    @WrapOperation(
            method = {"processMobExperience", "isImmuneToDamage", "getDamageProtection", "tickEffects",
                    "processEquipmentDropChance", "doPostAttackEffectsWithItemSource", "doPostAttackEffectsWithItemSourceOnBreak"},
            at = @At(value = "INVOKE", target = NEKOJS_EQUIPMENT_ITERATION)
    )
    private static void nekojs$gateEquipmentEffects(LivingEntity owner, @Coerce Object visitor, Operation<Void> original) {
        StageEnchantmentUse.withSlotVisitor(visitor, false, () -> original.call(owner, visitor));
    }

    @WrapOperation(
            method = {"doPostAttackEffectsWithItemSource", "doPostAttackEffectsWithItemSourceOnBreak", "doPostPiercingAttackEffects"},
            at = @At(value = "INVOKE", target = NEKOJS_SLOT_ITERATION)
    )
    private static void nekojs$gateWeaponEffects(ItemStack stack, EquipmentSlot slot, LivingEntity owner,
            @Coerce Object visitor, Operation<Void> original) {
        StageEnchantmentUse.withSlotVisitor(visitor, false, () -> original.call(stack, slot, owner, visitor));
    }

    @WrapOperation(
            method = "runLocationChangedEffects(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;)V",
            at = @At(value = "INVOKE", target = NEKOJS_EQUIPMENT_ITERATION)
    )
    private static void nekojs$gateEquippedLocationEffects(LivingEntity owner, @Coerce Object visitor, Operation<Void> original) {
        StageEnchantmentUse.withSlotVisitor(visitor, true, () -> original.call(owner, visitor));
    }

    @WrapOperation(
            method = "runLocationChangedEffects(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/EquipmentSlot;)V",
            at = @At(value = "INVOKE", target = NEKOJS_SLOT_ITERATION)
    )
    private static void nekojs$gateItemLocationEffects(ItemStack stack, EquipmentSlot slot, LivingEntity owner,
            @Coerce Object visitor, Operation<Void> original) {
        StageEnchantmentUse.withSlotVisitor(visitor, true, () -> original.call(stack, slot, owner, visitor));
    }

    @WrapOperation(
            method = "getRandomItemWith",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/enchantment/Enchantment;matchingSlot(Lnet/minecraft/world/entity/EquipmentSlot;)Z")
    )
    private static boolean nekojs$filterRandomItemEffects(Enchantment enchantment, EquipmentSlot slot,
            Operation<Boolean> original, @Local(argsOnly = true) LivingEntity owner,
            @Local Holder<Enchantment> holder, @Local Object2IntMap.Entry<?> entry) {
        return original.call(enchantment, slot)
                && nekojs$allows(StageDamageActors.fromEntity(owner), holder, entry.getIntValue());
    }

    @Inject(method = "getItemEnchantmentLevel", at = @At("RETURN"), cancellable = true)
    private static void nekojs$filterMiningToolLevel(Holder<Enchantment> enchantment, @Coerce Object tool,
            CallbackInfoReturnable<Integer> cir) {
        if (!nekojs$allows(StageEnchantmentUse.miningActorFor(tool), enchantment, cir.getReturnValueI())) {
            cir.setReturnValue(0);
        }
    }

    @Inject(method = "getEnchantmentLevel", at = @At("RETURN"), cancellable = true)
    private static void nekojs$filterEnchantmentLevel(Holder<Enchantment> enchantment, LivingEntity owner,
            CallbackInfoReturnable<Integer> cir) {
        Player actor = StageDamageActors.fromEntity(owner);
        if (nekojs$allows(actor, enchantment, cir.getReturnValueI())) {
            return;
        }
        int allowedLevel = 0;
        for (ItemStack stack : enchantment.value().getSlotItems(owner).values()) {
            int level = EnchantmentHelper.getItemEnchantmentLevel(enchantment, stack);
            if (level > allowedLevel && nekojs$allows(actor, enchantment, level)) {
                allowedLevel = level;
            }
        }
        cir.setReturnValue(allowedLevel);
    }

    @Unique
    private static boolean nekojs$allows(@Nullable Player actor, Holder<Enchantment> enchantment, int level) {
        return actor == null || actor.level().isClientSide() || level <= 0
                || StageGateEnforcer.allowUseEnchantment(actor, enchantment, level);
    }
}
