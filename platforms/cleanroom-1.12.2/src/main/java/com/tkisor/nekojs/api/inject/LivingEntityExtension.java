package com.tkisor.nekojs.api.inject;

import com.tkisor.nekojs.api.annotation.RemapByPrefix;
import com.tkisor.nekojs.api.spec.inject.LivingEntitySpec;
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
 * <p>实现 {@link LivingEntitySpec}，为每个 spec 方法提供 CR 平台的 {@code default} 实现。
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
 * <p><b>不实现的方法（CR 原生零参同名，行为一致，JS 直接用原生）：</b>
 * {@code getHealth()} / {@code setHealth(float)} / {@code getMaxHealth()} / {@code heal(float)}
 * —— CR 原生 {@link EntityLivingBase} 已有同名零参方法，不注入 neko 版以避免 Graal 分派冲突。
 *
 * @see EntityLivingBase
 * @see LivingEntitySpec
 */
@RemapByPrefix("neko$")
public interface LivingEntityExtension extends LivingEntitySpec {

    private EntityLivingBase self() {
        return (EntityLivingBase) this;
    }

    /**
     * 对实体造成伤害。CR 必须显式传 {@link DamageSource}，这里用 {@link DamageSource#GENERIC}。
     *
     * @param amount 伤害量
     */
    @Override
    default void neko$damage(float amount) {
        self().attackEntityFrom(DamageSource.GENERIC, amount);
    }

    /**
     * 主手物品。CR 原生无 {@code getMainHandItem()}（为 {@code getHeldItemMainhand()}），
     * 故保留 neko 注入版，用 {@code getHeldItem(EnumHand.MAIN_HAND)}。
     *
     * @return 主手物品栈
     */
    default ItemStack neko$getMainHandItem() {
        return self().getHeldItem(EnumHand.MAIN_HAND);
    }

    /**
     * 副手物品。CR 用 {@code getHeldItem(EnumHand.OFF_HAND)}。
     *
     * @return 副手物品栈
     */
    @Override
    default Object neko$getOffHandItem() {
        return self().getHeldItem(EnumHand.OFF_HAND);
    }

    /**
     * 按字符串 id 添加药水效果。CR 用 {@link ForgeRegistries#POTIONS} 查找 {@link Potion}，再
     * {@link EntityLivingBase#addPotionEffect(PotionEffect)}。
     *
     * @param effectId      药水 id，如 {@code "minecraft:speed"} 或 {@code "speed"}
     * @param duration      持续 tick
     * @param amplifier     放大器（0 = 等级 I）
     * @return {@code true} 若药水存在且成功添加
     */
    @Override
    default boolean neko$addEffect(String effectId, int duration, int amplifier) {
        Potion potion = lookupPotion(effectId);
        if (potion == null) return false;
        self().addPotionEffect(new PotionEffect(potion, duration, amplifier));
        return true;
    }

    /**
     * 按字符串 id 移除药水效果。CR 用 {@link EntityLivingBase#removePotionEffect(Potion)}。
     *
     * @param effectId 药水 id
     * @return {@code true} 若药水存在且成功移除
     */
    @Override
    default boolean neko$removeEffect(String effectId) {
        Potion potion = lookupPotion(effectId);
        if (potion == null) return false;
        self().removePotionEffect(potion);
        return true;
    }

    @Override
    default Object neko$getHeadItem() {
        return self().getItemStackFromSlot(EntityEquipmentSlot.HEAD);
    }

    @Override
    default Object neko$getChestItem() {
        return self().getItemStackFromSlot(EntityEquipmentSlot.CHEST);
    }

    @Override
    default Object neko$getLegsItem() {
        return self().getItemStackFromSlot(EntityEquipmentSlot.LEGS);
    }

    @Override
    default Object neko$getFeetItem() {
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
