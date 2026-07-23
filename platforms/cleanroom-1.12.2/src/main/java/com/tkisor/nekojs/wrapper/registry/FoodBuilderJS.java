package com.tkisor.nekojs.wrapper.registry;

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
public class FoodBuilderJS {
    private int healAmount = 1;
    private float saturationModifier = 0.1f;
    private boolean alwaysEdible = false;
    private boolean wolfFood = false;
    private final List<EffectEntry> effects = new ArrayList<>();

    private record EffectEntry(ResourceLocation potionId, int duration, int amplifier, float probability) {}

    public FoodBuilderJS() {}

    // ===================== 链式 builder =====================

    public FoodBuilderJS nutrition(int healAmount) { this.healAmount = healAmount; return this; }

    /** {@code nutrition} 的别名（与 1.21 API 对齐）。 */
    public FoodBuilderJS healAmount(int healAmount) { this.healAmount = healAmount; return this; }

    public FoodBuilderJS saturation(float saturationModifier) { this.saturationModifier = saturationModifier; return this; }

    public FoodBuilderJS alwaysEat() { this.alwaysEdible = true; return this; }

    /** {@code alwaysEat} 的别名（与 1.12 ItemFood 原生 API 对齐）。 */
    public FoodBuilderJS alwaysEdible() { this.alwaysEdible = true; return this; }

    public FoodBuilderJS wolfFood(boolean wolfFood) { this.wolfFood = wolfFood; return this; }

    /**
     * 添加药水效果。
     *
     * @param potionId    药水 id，如 "minecraft:strength" 或 "strength"
     * @param duration    持续 tick（20 tick = 1 秒）
     * @param amplifier   等级（0 = I, 1 = II）
     * @param probability 触发概率（0.0 ~ 1.0）
     */
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
