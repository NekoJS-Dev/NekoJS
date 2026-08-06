package com.tkisor.nekojs.api.inject;

import com.tkisor.nekojs.api.annotation.RemapByPrefix;
import com.tkisor.nekojs.api.spec.inject.LivingEntitySpec;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;

/**
 * @see LivingEntity
 */
@RemapByPrefix("neko$")
public interface LivingEntityExtension extends LivingEntitySpec {

    private LivingEntity self() {
        return (LivingEntity) this;
    }

    private MobEffect resolveEffect(String effectId) {
        if (effectId == null) {
            return null;
        }
        ResourceLocation loc = effectId.contains(":")
            ? ResourceLocation.tryParse(effectId)
            : ResourceLocation.tryParse("minecraft:" + effectId);
        if (loc == null) {
            return null;
        }
        return BuiltInRegistries.MOB_EFFECT.getOptional(loc).orElse(null);
    }

    @Override
    default boolean neko$addEffect(String effectId, int duration, int amplifier) {
        MobEffect effect = resolveEffect(effectId);
        if (effect == null) {
            return false;
        }
        return self().addEffect(new MobEffectInstance(BuiltInRegistries.MOB_EFFECT.wrapAsHolder(effect), duration, amplifier));
    }

    @Override
    default boolean neko$removeEffect(String effectId) {
        MobEffect effect = resolveEffect(effectId);
        if (effect == null) {
            return false;
        }
        return self().removeEffect(BuiltInRegistries.MOB_EFFECT.wrapAsHolder(effect));
    }

    @Override
    default Object neko$getHeadItem() {
        return self().getItemBySlot(EquipmentSlot.HEAD);
    }

    @Override
    default Object neko$getChestItem() {
        return self().getItemBySlot(EquipmentSlot.CHEST);
    }

    @Override
    default Object neko$getLegsItem() {
        return self().getItemBySlot(EquipmentSlot.LEGS);
    }

    @Override
    default Object neko$getFeetItem() {
        return self().getItemBySlot(EquipmentSlot.FEET);
    }

    @Override
    default void neko$damage(float amount) {
        self().hurt(self().damageSources().generic(), amount);
    }

    @Override
    default Object neko$getOffHandItem() {
        return self().getOffhandItem();
    }
}
