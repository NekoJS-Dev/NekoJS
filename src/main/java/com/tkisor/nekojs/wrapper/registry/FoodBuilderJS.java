// 26.x implementation; no version guard should appear in this file again. The 1.21.1 implementation is the
// file of the same name under versions/1.21.1/src, so changing this file's behavior means updating it too.
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

    /** Detaches collected food configuration from a callback-retained builder. */
    @com.tkisor.nekojs.api.annotation.HideFromJS
    public FoodBuilderJS snapshot() {
        FoodBuilderJS copy = new FoodBuilderJS();
        copy.nutrition = nutrition;
        copy.saturation = saturation;
        copy.alwaysEat = alwaysEat;
        copy.fastEat = fastEat;
        copy.effects.addAll(effects);
        return copy;
    }

    public FoodBuilderJS nutrition(int nutrition) { this.nutrition = nutrition; return this; }
    public FoodBuilderJS saturation(float saturation) { this.saturation = saturation; return this; }
    public FoodBuilderJS alwaysEat() { this.alwaysEat = true; return this; }
    public FoodBuilderJS fastEat() { this.fastEat = true; return this; }

    /** Accepts script identifiers without depending on a platform-wide host conversion. */
    public FoodBuilderJS effect(String effectId, int durationTicks, int amplifier, float probability) {
        return effect(effectId == null ? (Identifier) null : Identifier.parse(effectId), durationTicks, amplifier, probability);
    }

    /**
     * Adds a mob effect.
     * @param effectId effect id, such as "strength" or "minecraft:strength"
     * @param durationTicks duration in ticks (20 ticks = 1 second)
     * @param amplifier level (0 = level I, 1 = level II)
     * @param probability chance of getting this effect (0.0 ~ 1.0)
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
