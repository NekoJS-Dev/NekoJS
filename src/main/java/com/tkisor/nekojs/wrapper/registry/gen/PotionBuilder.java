package com.tkisor.nekojs.wrapper.registry.gen;
//~ mc_legacy_api

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.alchemy.Potion;
import java.util.ArrayList;
import java.util.List;

/**
 * Potion builder: effect instances are appended through {@code effect(...)}, which stays a method
 * because configuration is additive. The default is an empty effect list, as used for brewing bases.
 * <pre>
 * event.potion('mymod:strong_health', b =&gt; { b.effect('minecraft:regeneration', 400, 1) })
 * </pre>
 */
public class PotionBuilder extends RegistryObjectBuilder<Potion> {

    private final List<MobEffectInstance> effects = new ArrayList<>();

    public PotionBuilder(Identifier id) {
        super(id);
    }

    /**
     * Appends one effect instance.
     *
     * @param effect        the effect: an id string such as {@code 'minecraft:speed'} or a Holder
     * @param durationTicks duration in ticks
     * @param amplifier     amplifier level, starting at 0
     * @param ambient       whether this is an ambient effect (translucent HUD icon)
     * @param visible       whether to show particles and the icon
     */
    public void effect(Object effect, int durationTicks, int amplifier, boolean ambient, boolean visible) {
        Holder<MobEffect> holder = resolveEffect(effect);
        if (holder != null) {
            effects.add(new MobEffectInstance(holder, durationTicks, amplifier, ambient, visible));
        }
    }

    /** Convenience overload: not ambient, visible. */
    public void effect(Object effect, int durationTicks, int amplifier) {
        effect(effect, durationTicks, amplifier, false, true);
    }

    @Override
    public Potion build() {
        // Use namespace:path as the internal Potion name; brewing and language keys refer to it
        return new Potion(id.getNamespace() + "." + id.getPath(), effects.toArray(new MobEffectInstance[0]));
    }

    private static Holder<MobEffect> resolveEffect(Object value) {
        if (value instanceof Holder<?> holder && holder.value() instanceof MobEffect mobEffect) {
            return BuiltInRegistries.MOB_EFFECT.wrapAsHolder(mobEffect);
        }
        if (value instanceof String effectId) {
            ResourceKey<MobEffect> key = ResourceKey.create(Registries.MOB_EFFECT, Identifier.parse(effectId));
            // In 26.x Registry#get(ResourceKey) returns Optional<Reference>, which is used directly as a Holder.
            //? if >=26 {
            return BuiltInRegistries.MOB_EFFECT.get(key).map(ref -> (Holder<MobEffect>) ref).orElse(null);
//?} else {
            /*MobEffect mobEffect = BuiltInRegistries.MOB_EFFECT.get(key);
            return mobEffect == null ? null : BuiltInRegistries.MOB_EFFECT.wrapAsHolder(mobEffect);
*///?}
        }
        return null;
    }
}
