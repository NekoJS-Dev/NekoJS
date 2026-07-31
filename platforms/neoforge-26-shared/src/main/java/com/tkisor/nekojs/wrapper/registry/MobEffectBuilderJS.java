package com.tkisor.nekojs.wrapper.registry;

import lombok.Getter;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * 状态效果注册器（{@code StartupEvents.registry('mobEffect')}）。
 *
 * <p>脚本文本指定类别（{@code 'beneficial'} / {@code 'harmful'} / {@code 'neutral'}）
 * 与颜色（ARGB int）；类别决定 HUD 图标底色（beneficial/harmful）。
 */
public class MobEffectBuilderJS {
    @Getter
    private final Identifier location;

    private MobEffectCategory category = MobEffectCategory.NEUTRAL;
    private int color = 0xFFFFFF;

    public MobEffectBuilderJS(Identifier location) {
        this.location = location;
    }

    public MobEffectBuilderJS category(String categoryStr) {
        this.category = switch (categoryStr.toLowerCase()) {
            case "beneficial" -> MobEffectCategory.BENEFICIAL;
            case "harmful" -> MobEffectCategory.HARMFUL;
            default -> MobEffectCategory.NEUTRAL;
        };
        return this;
    }

    public MobEffectBuilderJS color(int color) {
        this.color = color;
        return this;
    }

    public MobEffect create() {
        // MobEffect 的构造器为 protected，用匿名子类实例化（不做任何方法覆盖）。
        return new MobEffect(category, color) {};
    }
}
