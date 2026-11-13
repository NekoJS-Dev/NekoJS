package com.tkisor.nekojs.js.type_adapter;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.alchemy.Potion;

/**
 * Adapter for {@link Potion}: a pure id lookup delegated to {@link SimpleRegistryBasedAdapter}.
 * Accepts a string id (with {@code minecraft:} added when missing), {@code NekoId}, {@code Identifier},
 * or a host of the target type itself.
 */
public final class PotionAdapter extends SimpleRegistryBasedAdapter<Potion> {
    public PotionAdapter() {
        super(BuiltInRegistries.POTION, Potion.class, "Potion");
    }
}
