package com.tkisor.nekojs.api.inject;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * @see LivingEntity
 * @author ZZZank
 */
public interface LivingEntityExtension {

    private LivingEntity self() {
        return (LivingEntity) this;
    }

    default boolean neko$addEffect(MobEffect effect, int durationTicks, int amplifier) {
        if (effect == null) {
            return false;
        }
        return self().addEffect(new MobEffectInstance(BuiltInRegistries.MOB_EFFECT.wrapAsHolder(effect), durationTicks, amplifier));
    }

    default boolean neko$removeEffect(MobEffect effect) {
        if (effect == null) {
            return false;
        }
        return self().removeEffect(BuiltInRegistries.MOB_EFFECT.wrapAsHolder(effect));
    }

    default ItemStack neko$getHeadItem() {
        return self().getItemBySlot(EquipmentSlot.HEAD);
    }

    default ItemStack neko$getChestItem() {
        return self().getItemBySlot(EquipmentSlot.CHEST);
    }

    default ItemStack neko$getLegsItem() {
        return self().getItemBySlot(EquipmentSlot.LEGS);
    }

    default ItemStack neko$getFeetItem() {
        return self().getItemBySlot(EquipmentSlot.FEET);
    }
}