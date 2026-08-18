package com.tkisor.nekojs.wrapper.registry;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.food.FoodProperties;

import java.util.ArrayList;
import java.util.List;

public class FoodBuilderJS {
    private int nutrition = 1;
    private float saturation = 0.1f;
    private boolean alwaysEat = false;
    private boolean fastEat = false;

    private record EffectEntry(ResourceLocation effectId, int durationTicks, int amplifier, float probability) {}
    private final List<EffectEntry> effects = new ArrayList<>();

    public FoodBuilderJS() {}

    public FoodBuilderJS nutrition(int nutrition) { this.nutrition = nutrition; return this; }
    public FoodBuilderJS saturation(float saturation) { this.saturation = saturation; return this; }
    public FoodBuilderJS alwaysEat() { this.alwaysEat = true; return this; }
    public FoodBuilderJS fastEat() { this.fastEat = true; return this; }

    /**
     * 添加药水效果
     * @param effectId 效果ID，如 "strength" 或 "minecraft:strength"
     * @param durationTicks 持续时间 (20 tick = 1秒)
     * @param amplifier 等级 (0 = I级, 1 = II级)
     * @param probability 获得该效果的概率 (0.0 ~ 1.0)
     */
    public FoodBuilderJS effect(ResourceLocation effectId, int durationTicks, int amplifier, float probability) {
        if (effectId != null) {
            this.effects.add(new EffectEntry(effectId, durationTicks, amplifier, probability));
        }
        return this;
    }

    public FoodProperties buildFood() {
        FoodProperties.Builder builder = new FoodProperties.Builder()
                .nutrition(this.nutrition)
                .saturationModifier(this.saturation);

        if (this.alwaysEat) {
            builder.alwaysEdible();
        }

        // 1.21.1 中，快速进食（16 ticks，即 0.8 秒）由 fast() 方法控制
        if (this.fastEat) {
            builder.fast();
        }

        // 1.21.1 中，药水效果直接添加在 FoodProperties 上
        for (EffectEntry e : effects) {
            // 1.21.1 推荐使用 getHolder 来获取包含 Registry 信息的 Holder 对象；
            // effect 用 Supplier 重载（直接传 MobEffectInstance 的旧重载已废弃）
            BuiltInRegistries.MOB_EFFECT.getHolder(e.effectId()).ifPresent(effectHolder ->
                    builder.effect(() -> new MobEffectInstance(effectHolder, e.durationTicks(), e.amplifier()), e.probability())
            );
        }

        return builder.build();
    }

}