package com.tkisor.nekojs.js.type_adapter;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.CreativeModeTab;

/**
 * Adapter for {@link CreativeModeTab}: a pure id lookup delegated to {@link SimpleRegistryBasedAdapter}.
 * Accepts a string id (with {@code minecraft:} added when missing), {@code NekoId}, {@code Identifier},
 * or a host of the target type itself.
 */
public final class CreativeModeTabAdapter extends SimpleRegistryBasedAdapter<CreativeModeTab> {
    public CreativeModeTabAdapter() {
        super(BuiltInRegistries.CREATIVE_MODE_TAB, CreativeModeTab.class, "CreativeModeTab");
    }
}
