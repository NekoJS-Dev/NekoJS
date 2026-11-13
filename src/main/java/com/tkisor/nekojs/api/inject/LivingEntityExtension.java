package com.tkisor.nekojs.api.inject;

import com.tkisor.nekojs.api.annotation.RemapByPrefix;
import com.tkisor.nekojs.api.spec.inject.LivingEntitySpec;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
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
//? if >=26 {
        Identifier loc = Identifier.parse(effectId.contains(":") ? effectId : "minecraft:" + effectId);
//?} else {
        /*Identifier loc = effectId.contains(":")
            ? Identifier.tryParse(effectId)
            : Identifier.tryParse("minecraft:" + effectId);
        if (loc == null) {
            return null;
        }
*///?}
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
    // hurt(DamageSource, float) is deprecated in 26.1 (1.21.2), which added hurtServer; it is final and
    // non-deprecated again in 26.2. The same shared code compiles against both versions, and
    // @SuppressWarnings("deprecation") is harmless on each (hurt is not deprecated in 1.21.1), so
    // no version guard is used.
    @SuppressWarnings("deprecation")
    default void neko$damage(float amount) {
        self().hurt(self().damageSources().generic(), amount);
    }

    @Override
    default Object neko$getOffHandItem() {
        return self().getOffhandItem();
    }
}
