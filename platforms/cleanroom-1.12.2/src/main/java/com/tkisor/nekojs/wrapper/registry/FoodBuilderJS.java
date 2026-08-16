package com.tkisor.nekojs.wrapper.registry;

import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.api.annotation.Return;
import net.minecraft.item.ItemFood;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/**
 * 1.12.2 FoodBuilderJS。
 *
 * <p>1.12.2 无 FoodProperties/Consumable（那是 1.21+）。食物 = {@link ItemFood} 子类：
 * <ul>
 *   <li>{@code new ItemFood(int healAmount, float saturationModifier, boolean isWolfFood)}</li>
 *   <li>{@link ItemFood#setAlwaysEdible()} 让玩家满饱食度时仍可吃</li>
 *   <li>{@link ItemFood#setPotionEffect(PotionEffect, float)} 附带药水效果（按概率触发）</li>
 * </ul>
 *
 * <p>{@link #build()} 返回 {@link ItemFood}（registryName / translationKey / creativeTab 由
 * {@link ItemBuilderJS} 统一设置）。
 */
@Doc("Food properties builder used inside ItemBuilderJS.food(callback).")
@Doc("1.12.2 food is an ItemFood subclass: heal amount, saturation modifier, always-edible, and potion effects.")
public class FoodBuilderJS {
    private int healAmount = 1;
    private float saturationModifier = 0.1f;
    private boolean alwaysEdible = false;
    private boolean wolfFood = false;
    private final List<EffectEntry> effects = new ArrayList<>();

    private record EffectEntry(ResourceLocation potionId, int duration, int amplifier, float probability) {}

    public FoodBuilderJS() {}

    // ===================== 链式 builder =====================

    /** 设置恢复的饱食度点数。 */
    @Doc("Sets how many hunger points the food restores.")
    @Param(name = "healAmount", value = "hunger points restored, e.g. 4 for a standard meal")
    @Return("this builder, for chaining")
    public FoodBuilderJS nutrition(int healAmount) { this.healAmount = healAmount; return this; }

    /** {@code nutrition} 的别名（与 1.21 API 对齐）。 */
    @Doc("Alias of nutrition(healAmount), aligned with the 1.21 API.")
    @Param(name = "healAmount", value = "hunger points restored")
    @Return("this builder, for chaining")
    public FoodBuilderJS healAmount(int healAmount) { this.healAmount = healAmount; return this; }

    /** 设置饱和度修饰符。 */
    @Doc("Sets the saturation modifier of the food.")
    @Param(name = "saturationModifier", value = "saturation modifier; higher keeps the hunger bar full longer")
    @Return("this builder, for chaining")
    public FoodBuilderJS saturation(float saturationModifier) { this.saturationModifier = saturationModifier; return this; }

    /** 允许满饱食度时仍可食用。 */
    @Doc("Allows eating the food even when the hunger bar is full.")
    @Return("this builder, for chaining")
    public FoodBuilderJS alwaysEat() { this.alwaysEdible = true; return this; }

    /** {@code alwaysEat} 的别名（与 1.12 ItemFood 原生 API 对齐）。 */
    @Doc("Alias of alwaysEat(), aligned with the 1.12 ItemFood API.")
    @Return("this builder, for chaining")
    public FoodBuilderJS alwaysEdible() { this.alwaysEdible = true; return this; }

    /** 设置狼是否可食用。 */
    @Doc("Sets whether wolves can eat this food.")
    @Param(name = "wolfFood", value = "true if wolves can eat it")
    @Return("this builder, for chaining")
    public FoodBuilderJS wolfFood(boolean wolfFood) { this.wolfFood = wolfFood; return this; }

    /**
     * 添加药水效果。
     *
     * @param potionId    药水 id，如 "minecraft:strength" 或 "strength"
     * @param duration    持续 tick（20 tick = 1 秒）
     * @param amplifier   等级（0 = I, 1 = II）
     * @param probability 触发概率（0.0 ~ 1.0）
     */
    @Doc("Adds a potion effect applied when the food is eaten.")
    @Param(name = "potionId", value = "potion id like 'minecraft:strength' or 'strength'")
    @Param(name = "duration", value = "effect duration in ticks (20 ticks = 1 second)")
    @Param(name = "amplifier", value = "effect level; 0 is level I, 1 is level II")
    @Param(name = "probability", value = "chance the effect triggers, from 0.0 to 1.0")
    @Return("this builder, for chaining")
    public FoodBuilderJS effect(ResourceLocation potionId, int duration, int amplifier, float probability) {
        if (potionId != null) {
            this.effects.add(new EffectEntry(potionId, duration, amplifier, probability));
        }
        return this;
    }

    // ===================== build =====================

    /**
     * 构建 {@link ItemFood} 实例。registryName / translationKey 由 {@link ItemBuilderJS} 设置。
     * 未注册的 potionId 静默跳过（保持 forge 原生宽容行为）。
     */
    @Doc("Builds the ItemFood instance; registry name and translation key are set by ItemBuilderJS.")
    @Doc("Potion ids that are not registered are silently skipped.")
    @Return("the configured ItemFood")
    public ItemFood build() {
        ItemFood food = new ItemFood(healAmount, saturationModifier, wolfFood);
        if (alwaysEdible) {
            food.setAlwaysEdible();
        }
        for (EffectEntry e : effects) {
            Potion potion = ForgeRegistries.POTIONS.getValue(e.potionId());
            if (potion != null) {
                food.setPotionEffect(new PotionEffect(potion, e.duration(), e.amplifier()), e.probability());
            }
        }
        return food;
    }
}
