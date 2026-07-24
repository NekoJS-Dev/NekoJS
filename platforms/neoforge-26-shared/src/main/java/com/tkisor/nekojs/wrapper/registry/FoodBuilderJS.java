package com.tkisor.nekojs.wrapper.registry;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;

import java.util.ArrayList;
import java.util.List;

public class FoodBuilderJS {
    private int nutrition = 1;
    private float saturation = 0.1f;
    private boolean alwaysEat = false;
    private boolean fastEat = false;

    private record EffectEntry(Identifier effectId, int durationTicks, int amplifier, float probability) {}
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
    public FoodBuilderJS effect(Identifier effectId, int durationTicks, int amplifier, float probability) {
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
        return builder.build();
    }

    public Consumable buildConsumable() {
        Consumable.Builder builder = Consumable.builder();

        builder.consumeSeconds(this.fastEat ? 0.8F : 1.6F);

        for (EffectEntry e : effects) {
            BuiltInRegistries.MOB_EFFECT.get(e.effectId()).ifPresent(effectHolder ->
                    builder.onConsume(new ApplyStatusEffectsConsumeEffect(
                            new MobEffectInstance(effectHolder, e.durationTicks(), e.amplifier()),
                            e.probability()
                    ))
            );
        }
        return builder.build();
    }
}