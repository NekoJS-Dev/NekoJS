package com.tkisor.nekojs.wrapper.registry;

import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.api.annotation.Return;
import net.minecraft.potion.Potion;

/**
 * 1.12.2 状态效果注册器（{@code StartupEvents.registry('mobEffect')}）。
 *
 * <p>1.12.2 的 {@link Potion} 是具体类（非抽象），构造器 protected；
 * 用匿名子类实例化，可选覆盖 {@code isReady}/{@code performEffect} 实现 tick 行为。
 * 注册到 {@code ForgeRegistries.POTIONS}（分发超类型 Potion.class）。
 */
@Doc("Builder for registering a new mob effect; obtain it from RegistryEvents.mobEffect.create(id).")
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
    @Doc("Marks the effect as bad (negative), tinting the HUD and potion base.")
    @Param(name = "bad", value = "true for a negative effect")
    @Return("this builder, for chaining")
    public MobEffectBuilderJS bad(boolean bad) {
        this.badEffect = bad;
        return this;
    }

    /** 液体颜色（ARGB int）。 */
    @Doc("Sets the effect's liquid color (particle and potion tint).")
    @Param(name = "color", value = "color as an ARGB integer like 0xFF8000FF")
    @Return("this builder, for chaining")
    public MobEffectBuilderJS color(int color) {
        this.liquidColor = color;
        return this;
    }

    /** 图标在 potions.png 中的网格坐标（需 {@code hasIcon(true)} 才渲染）。 */
    @Doc("Sets the effect icon as a grid coordinate in potions.png and enables it.")
    @Param(name = "x", value = "icon column in the potions.png grid")
    @Param(name = "y", value = "icon row in the potions.png grid")
    @Return("this builder, for chaining")
    public MobEffectBuilderJS icon(int x, int y) {
        this.iconX = x;
        this.iconY = y;
        this.hasIcon = true;
        return this;
    }

    /** 是否显示状态图标。 */
    @Doc("Toggles the status icon in the HUD.")
    @Param(name = "has", value = "true to render the effect icon")
    @Return("this builder, for chaining")
    public MobEffectBuilderJS hasIcon(boolean has) {
        this.hasIcon = has;
        return this;
    }

    /** 注册名。 */
    @Doc("Gets the registry name of the effect being built.")
    @Return("the registry name string")
    public String getRegistryName() {
        return registryName;
    }

    /** 构建效果实例（匿名 Potion 子类）。 */
    @Doc("Builds the effect as an anonymous Potion subclass; registration happens when the event completes.")
    @Return("the configured potion effect")
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
