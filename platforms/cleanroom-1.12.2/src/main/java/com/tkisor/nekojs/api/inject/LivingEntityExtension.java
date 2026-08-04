package com.tkisor.nekojs.api.inject;

import com.tkisor.nekojs.api.annotation.RemapByPrefix;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

/**
 * 1.12.2 {@link EntityLivingBase} 统一扩展方法，注入到 MC 的 {@link EntityLivingBase} 类。
 *
 * <p>1.12.2 与 1.21.1 的关键差异：
 * <ul>
 *   <li>{@code LivingEntity} → {@link EntityLivingBase}</li>
 *   <li>装备槽是 {@link EntityEquipmentSlot}，取装备用 {@code getItemStackFromSlot}</li>
 *   <li>主/副手用 {@link EnumHand} 枚举 + {@code getHeldItem(EnumHand)}</li>
 *   <li>伤害用 {@link DamageSource}：{@code attackEntityFrom(DamageSource.GENERIC, float)}</li>
 *   <li>药水效果是 {@link Potion} 类（按 {@link ForgeRegistries#POTIONS} 查找），
 *       效果实例是 {@link PotionEffect}</li>
 *   <li>没有 Holder 包装，{@link Potion} 直接使用</li>
 * </ul>
 *
 * @see EntityLivingBase
 * @author ZZZank
 */
@RemapByPrefix("neko$")
public interface LivingEntityExtension {

    private EntityLivingBase self() {
        return (EntityLivingBase) this;
    }

    /**
     * 当前生命值。对齐 1.21.1 {@code getHealth()}。
     *
     * @return 生命值
     */
    default float neko$getHealth() {
        return self().getHealth();
    }

    /**
     * 设置生命值。对齐 1.21.1 {@code setHealth(float)}。
     *
     * @param health 目标生命值
     */
    default void neko$setHealth(float health) {
        self().setHealth(health);
    }

    /**
     * 最大生命值。对齐 1.21.1 {@code getMaxHealth()}。
     *
     * @return 最大生命值
     */
    default float neko$getMaxHealth() {
        return self().getMaxHealth();
    }

    /**
     * 治疗实体（不会超过最大生命值）。对齐 1.21.1 {@code heal(float)}。
     *
     * @param amount 治疗量
     */
    default void neko$heal(float amount) {
        self().heal(amount);
    }

    /**
     * 对实体造成伤害。对齐 1.21.1 {@code damage(float)}（1.21.1 内部用 {@code GENERIC} source）。
     * 1.12.2 必须显式传 {@link DamageSource}，这里用 {@link DamageSource#GENERIC}。
     *
     * @param amount 伤害量
     */
    default void neko$damage(float amount) {
        self().attackEntityFrom(DamageSource.GENERIC, amount);
    }

    /**
     * 主手物品。对齐 1.21.1 {@code getMainHandItem()}。
     * 1.12.2 用 {@code getHeldItem(EnumHand.MAIN_HAND)}。
     *
     * @return 主手物品栈
     */
    default ItemStack neko$getMainHandItem() {
        return self().getHeldItem(EnumHand.MAIN_HAND);
    }

    /**
     * 副手物品。对齐 1.21.1 {@code getOffHandItem()}。
     * 1.12.2 用 {@code getHeldItem(EnumHand.OFF_HAND)}。
     *
     * @return 副手物品栈
     */
    default ItemStack neko$getOffHandItem() {
        return self().getHeldItem(EnumHand.OFF_HAND);
    }

    /**
     * 按字符串 id 添加药水效果。对齐 1.21.1 {@code addEffect(MobEffect, int, int)}。
     * 1.12.2 用 {@link ForgeRegistries#POTIONS} 查找 {@link Potion}，再
     * {@link EntityLivingBase#addPotionEffect(PotionEffect)}。
     *
     * @param potionId 药水 id，如 {@code "minecraft:speed"} 或 {@code "speed"}
     * @param durationTicks 持续 tick
     * @param amplifier   放大器（0 = 等级 I）
     * @return {@code true} 若药水存在且成功添加
     */
    default boolean neko$addEffect(String potionId, int durationTicks, int amplifier) {
        Potion potion = lookupPotion(potionId);
        if (potion == null) return false;
        self().addPotionEffect(new PotionEffect(potion, durationTicks, amplifier));
        return true;
    }

    /**
     * 按字符串 id 移除药水效果。对齐 1.21.1 {@code removeEffect(MobEffect)}。
     * 1.12.2 用 {@link EntityLivingBase#removePotionEffect(Potion)}。
     *
     * @param potionId 药水 id
     * @return {@code true} 若药水存在且成功移除
     */
    default boolean neko$removeEffect(String potionId) {
        Potion potion = lookupPotion(potionId);
        if (potion == null) return false;
        self().removePotionEffect(potion);
        return true;
    }

    default ItemStack neko$getHeadItem() {
        return self().getItemStackFromSlot(EntityEquipmentSlot.HEAD);
    }

    default ItemStack neko$getChestItem() {
        return self().getItemStackFromSlot(EntityEquipmentSlot.CHEST);
    }

    default ItemStack neko$getLegsItem() {
        return self().getItemStackFromSlot(EntityEquipmentSlot.LEGS);
    }

    default ItemStack neko$getFeetItem() {
        return self().getItemStackFromSlot(EntityEquipmentSlot.FEET);
    }

    /**
     * 按 id 字符串查找 {@link Potion}。未找到返回 null。
     */
    private static Potion lookupPotion(String id) {
        if (id == null) return null;
        String normalized = id.contains(":") ? id : "minecraft:" + id;
        return ForgeRegistries.POTIONS.getValue(new ResourceLocation(normalized));
    }
}
