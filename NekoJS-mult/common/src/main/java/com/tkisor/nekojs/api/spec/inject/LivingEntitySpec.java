package com.tkisor.nekojs.api.spec.inject;

import com.tkisor.nekojs.api.annotation.RemapByPrefix;
import com.tkisor.nekojs.api.spec.PlatformAvailability;

/**
 * LivingEntity 跨平台统一扩展规范。
 *
 * <p>各平台的 {@code LivingEntityExtension} 必须 {@code extends LivingEntitySpec}，为每个 {@code neko$}
 * 方法提供平台特定的 {@code default} 实现。本接口的 {@code default} 方法体抛
 * {@link UnsupportedOperationException}——若平台 Extension 未覆盖某方法，运行时会明确报错。
 *
 * <p><b>碰撞排除（调研驱动）：</b>以下方法因两平台原生 {@code LivingEntity}/{@code EntityLivingBase}
 * 零参同名碰撞（Graal 无法分派）而<b>不</b>在本 spec 中：
 * <ul>
 *   <li>{@code getHealth()} / {@code setHealth(float)} / {@code getMaxHealth()} / {@code heal(float)}
 *       —— 两平台原生均有同名方法，行为一致，JS 直接用原生。
 *   <li>{@code getMainHandItem()} —— NF 原生有同名零参方法（CR 无碰撞但跨平台一致性要求排除）。
 * </ul>
 *
 * <p><b>参数 / 返回类型约定：</b>装备 getter 返回 {@code Object}（平台原生 ItemStack），
 * 药水效果按字符串 id 操作——spec 不声明具体 MC 类型以遵守 common-api 边界约束。
 */
@RemapByPrefix("neko$")
@PlatformAvailability(PlatformAvailability.Scope.ALL)
public interface LivingEntitySpec {

    /** 对实体造成伤害（通用伤害源）。 */
    default void neko$damage(float amount) {
        throw new UnsupportedOperationException("LivingEntitySpec.neko$damage not implemented");
    }

    /** 副手物品。 */
    default Object neko$getOffHandItem() {
        throw new UnsupportedOperationException("LivingEntitySpec.neko$getOffHandItem not implemented");
    }

    /** 头部装备。 */
    default Object neko$getHeadItem() {
        throw new UnsupportedOperationException("LivingEntitySpec.neko$getHeadItem not implemented");
    }

    /** 胸部装备。 */
    default Object neko$getChestItem() {
        throw new UnsupportedOperationException("LivingEntitySpec.neko$getChestItem not implemented");
    }

    /** 腿部装备。 */
    default Object neko$getLegsItem() {
        throw new UnsupportedOperationException("LivingEntitySpec.neko$getLegsItem not implemented");
    }

    /** 脚部装备。 */
    default Object neko$getFeetItem() {
        throw new UnsupportedOperationException("LivingEntitySpec.neko$getFeetItem not implemented");
    }

    /**
     * 按字符串 id 添加药水效果。
     *
     * @param effectId       药水 id，如 {@code "minecraft:speed"} 或 {@code "speed"}
     * @param duration       持续 tick
     * @param amplifier      放大器（0 = 等级 I）
     * @return {@code true} 若药水存在且成功添加
     */
    default boolean neko$addEffect(String effectId, int duration, int amplifier) {
        throw new UnsupportedOperationException("LivingEntitySpec.neko$addEffect not implemented");
    }

    /**
     * 按字符串 id 移除药水效果。
     *
     * @param effectId 药水 id
     * @return {@code true} 若药水存在且成功移除
     */
    default boolean neko$removeEffect(String effectId) {
        throw new UnsupportedOperationException("LivingEntitySpec.neko$removeEffect not implemented");
    }
}
