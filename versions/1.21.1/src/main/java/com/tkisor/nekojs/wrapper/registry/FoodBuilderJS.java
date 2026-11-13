// 1.21.1 implementation, paired with the same-named 26.x file under src/ in the version tree.
// The content is that file evaluated at this node; keep this file in sync when 26.x behavior changes.
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
        return effect(effectId == null ? (ResourceLocation) null : ResourceLocation.parse(effectId), durationTicks, amplifier, probability);
    }

    /**
     * Adds a mob effect to the food.
     *
     * @param effectId the effect id, such as "strength" or "minecraft:strength"
     * @param durationTicks how long the effect lasts in ticks (20 ticks = 1 second)
     * @param amplifier the effect level (0 = level I, 1 = level II)
     * @param probability the chance of getting the effect (0.0 to 1.0)
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
        if (this.fastEat) {
            builder.fast();
        }

        // In 1.21.1 the mob effects are added directly to FoodProperties
        for (EffectEntry e : effects) {
            // 1.21.1 prefers getHolder(), which returns a Holder that carries the registry information;
            // the effect overload takes a Supplier (the old overload that took a MobEffectInstance is deprecated)
            BuiltInRegistries.MOB_EFFECT.getHolder(e.effectId()).ifPresent(effectHolder ->
                    builder.effect(() -> new MobEffectInstance(effectHolder, e.durationTicks(), e.amplifier()), e.probability())
            );
        }
        return builder.build();
    }

}
