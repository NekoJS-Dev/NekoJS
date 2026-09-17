package com.tkisor.nekojs.core.dynamic.plan;

import java.util.Locale;
import java.util.Set;

/**
 * MobEffect 动态定义 builder（{@code event.mobEffect(id, cb)}）：HUD 分类与 ARGB 颜色。
 * 分类是与 vanilla MobEffectCategory 对应的封闭字符串集合（beneficial/harmful/neutral），
 * 由 Adapter 在合法应用点解析为 MC 枚举。
 */
public final class DynamicMobEffectBuilder extends DynamicDefinitionBuilder {

    public static final Set<String> CATEGORIES = Set.of("beneficial", "harmful", "neutral");

    private String category = "neutral";
    private int color = 0xFFFFFF;

    /** 分类：neutral（默认）/ beneficial / harmful；大小写不敏感，规范化为小写。 */
    public DynamicMobEffectBuilder setCategory(String category) {
        String normalized = DynamicDefinitionType.normalizeToken(category, "mob effect category");
        if (!CATEGORIES.contains(normalized)) {
            throw new IllegalArgumentException("Unknown mob effect category '" + category.trim()
                    + "': expected 'beneficial', 'harmful' or 'neutral'");
        }
        this.category = normalized;
        return this;
    }

    /** 效果颜色（ARGB int，如 {@code 0x8B0000}）。 */
    public DynamicMobEffectBuilder setColor(int color) {
        this.color = color;
        return this;
    }

    public String getCategory() {
        return category;
    }

    public int getColor() {
        return color;
    }

    @Override
    public String toString() {
        return "DynamicMobEffectBuilder[category=" + category + ", color=0x"
                + Integer.toHexString(color).toUpperCase(Locale.ROOT) + ", mode=" + getMode() + "]";
    }
}
