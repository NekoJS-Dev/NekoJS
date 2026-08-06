package com.tkisor.nekojs.api.spec.inject;

import com.tkisor.nekojs.api.annotation.RemapByPrefix;
import com.tkisor.nekojs.api.spec.PlatformAvailability;

/**
 * ItemStack 跨平台统一扩展规范（核心子集）。
 *
 * <p>只含 CR 也能实现的、且无原生碰撞的方法。各平台 {@code ItemStackExtension}
 * 必须 {@code extends ItemStackSpec}。
 *
 * <p><b>碰撞排除（调研驱动）：</b>以下方法因原生零参碰撞而不在 spec 中：
 * <ul>
 *   <li>{@code copy()} / {@code getItem()} —— 两平台原生均有同名零参方法，行为一致，JS 用原生
 *   <li>{@code setCount(int)} —— 两平台原生均有，neko 版返回 this（链式）vs 原生 void，语义不同
 *   <li>{@code enchant(Holder,int)} —— NF 原生碰撞（neko 的 String 版本保留但不进 spec）
 * </ul>
 */
@RemapByPrefix("neko$")
@PlatformAvailability(PlatformAvailability.Scope.ALL)
public interface ItemStackSpec {

    /** 返回指定数量的副本（不修改原 ItemStack）。 */
    default Object neko$withCount(int count) {
        throw new UnsupportedOperationException("ItemStackSpec.neko$withCount not implemented");
    }

    /** 检查是否拥有指定附魔。enchId 如 "minecraft:sharpness"。 */
    default boolean neko$hasEnchantment(String enchId, int level) {
        throw new UnsupportedOperationException("ItemStackSpec.neko$hasEnchantment not implemented");
    }

    /** 是否不可破坏。 */
    default boolean neko$isUnbreakable() {
        throw new UnsupportedOperationException("ItemStackSpec.neko$isUnbreakable not implemented");
    }

    /** 设置不可破坏。 */
    default void neko$setUnbreakable(boolean value) {
        throw new UnsupportedOperationException("ItemStackSpec.neko$setUnbreakable not implemented");
    }

    /** 检查是否与另一个 ItemStack 匹配（物品类型一致）。 */
    default boolean neko$matches(Object other) {
        throw new UnsupportedOperationException("ItemStackSpec.neko$matches not implemented");
    }
}
