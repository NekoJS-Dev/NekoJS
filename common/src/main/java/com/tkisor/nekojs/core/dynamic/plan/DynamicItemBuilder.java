package com.tkisor.nekojs.core.dynamic.plan;

import java.util.Set;

/**
 * Item 动态定义 builder（ticket 16，类型直达入口 {@code event.item(id, cb)} 的
 * typed callback Builder）。与旧 {@code DynamicRegistryJS.ItemBuilder} 的配置形状
 * 保持一致（stack size / rarity / fire resistant / mode），但值在校验+规范化后进入
 * inert 计划，不在候选期创建任何 MC 对象（Rarity 等 MC 枚举由 Adapter 在合法应用点
 * 解析——本类只持有规范化的字符串值）。
 */
public final class DynamicItemBuilder extends DynamicDefinitionBuilder {

    /** vanilla Item.Properties#stacksTo 的合法区间。 */
    public static final int MAX_STACK_SIZE_MIN = 1;
    public static final int MAX_STACK_SIZE_MAX = 99;

    /** 与 vanilla Rarity 对应的封闭字符串集合（小写规范化后比较）。 */
    public static final Set<String> RARITIES = Set.of("common", "uncommon", "rare", "epic");

    private int maxStackSize = 64;
    private String rarity = "common";
    private boolean fireResistant;

    /** 最大堆叠（1..99，默认 64）；超界在写入点抛错（两种写入形态同一校验）。 */
    public DynamicItemBuilder setMaxStackSize(int size) {
        if (size < MAX_STACK_SIZE_MIN || size > MAX_STACK_SIZE_MAX) {
            throw new IllegalArgumentException(
                    "Invalid maxStackSize " + size + ": must be between " + MAX_STACK_SIZE_MIN + " and "
                            + MAX_STACK_SIZE_MAX + " (got " + size + ")");
        }
        this.maxStackSize = size;
        return this;
    }

    /** 稀有度：common（默认）/ uncommon / rare / epic；大小写不敏感，规范化为小写。 */
    public DynamicItemBuilder setRarity(String rarity) {
        String normalized = DynamicDefinitionType.normalizeToken(rarity, "rarity");
        if (!RARITIES.contains(normalized)) {
            throw new IllegalArgumentException(
                    "Unknown rarity '" + rarity.trim() + "': expected one of common, uncommon, rare, epic");
        }
        this.rarity = normalized;
        return this;
    }

    /** 是否防火（默认 false）。 */
    public DynamicItemBuilder setFireResistant(boolean value) {
        this.fireResistant = value;
        return this;
    }

    public int getMaxStackSize() {
        return maxStackSize;
    }

    public String getRarity() {
        return rarity;
    }

    public boolean isFireResistant() {
        return fireResistant;
    }

    @Override
    public String toString() {
        return "DynamicItemBuilder[maxStackSize=" + maxStackSize + ", rarity=" + rarity
                + ", fireResistant=" + fireResistant + ", mode=" + getMode() + "]";
    }
}
