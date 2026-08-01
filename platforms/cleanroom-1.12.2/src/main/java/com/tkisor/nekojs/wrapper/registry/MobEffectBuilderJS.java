package com.tkisor.nekojs.wrapper.registry;

import net.minecraft.potion.Potion;

/**
 * 1.12.2 状态效果注册器（{@code StartupEvents.registry('mobEffect')}）。
 *
 * <p>1.12.2 的 {@link Potion} 是具体类（非抽象），构造器 protected；
 * 用匿名子类实例化，可选覆盖 {@code isReady}/{@code performEffect} 实现 tick 行为。
 * 注册到 {@code ForgeRegistries.POTIONS}（分发超类型 Potion.class）。
 */
public class MobEffectBuilderJS {

    private final String registryName;
    private boolean badEffect = false;
    private int liquidColor = 0xFFFFFF;
    private int iconX = 0;
    private int iconY = 0;
    private boolean hasIcon = false;

    public MobEffectBuilderJS(String registryName) {
        this.registryName = registryName;
    }

    /** 是否负面效果（HUD 红色调 / 药水基色）。 */
    public MobEffectBuilderJS bad(boolean bad) {
        this.badEffect = bad;
        return this;
    }

    /** 液体颜色（ARGB int）。 */
    public MobEffectBuilderJS color(int color) {
        this.liquidColor = color;
        return this;
    }

    /** 图标在 potions.png 中的网格坐标（需 {@code hasIcon(true)} 才渲染）。 */
    public MobEffectBuilderJS icon(int x, int y) {
        this.iconX = x;
        this.iconY = y;
        this.hasIcon = true;
        return this;
    }

    public MobEffectBuilderJS hasIcon(boolean has) {
        this.hasIcon = has;
        return this;
    }

    public String getRegistryName() {
        return registryName;
    }

    @SuppressWarnings("deprecation")
    public Potion build() {
        boolean withIcon = hasIcon;
        return new Potion(badEffect, liquidColor) {
            {
                if (withIcon) {
                    setIconIndex(iconX, iconY);
                }
            }

            @Override
            public boolean hasStatusIcon() {
                return withIcon;
            }
        };
    }
}
