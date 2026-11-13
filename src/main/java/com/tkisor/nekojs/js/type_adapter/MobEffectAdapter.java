package com.tkisor.nekojs.js.type_adapter;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;

/**
 * Adapter for {@link MobEffect}: a pure id lookup delegated to {@link SimpleRegistryBasedAdapter}.
 * Accepts a string id (with {@code minecraft:} added when missing), {@code NekoId}, {@code Identifier},
 * or a host of the target type itself.
 */
public final class MobEffectAdapter extends SimpleRegistryBasedAdapter<MobEffect> {
    public MobEffectAdapter() {
        super(BuiltInRegistries.MOB_EFFECT, MobEffect.class, "MobEffect");
    }
}
