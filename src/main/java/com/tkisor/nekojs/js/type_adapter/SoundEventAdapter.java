package com.tkisor.nekojs.js.type_adapter;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;

/**
 * Adapter for {@link SoundEvent}: a pure id lookup delegated to {@link SimpleRegistryBasedAdapter}.
 * Accepts a string id (with {@code minecraft:} added when missing), {@code NekoId}, {@code Identifier},
 * or a host of the target type itself.
 */
public final class SoundEventAdapter extends SimpleRegistryBasedAdapter<SoundEvent> {
    public SoundEventAdapter() {
        super(BuiltInRegistries.SOUND_EVENT, SoundEvent.class, "SoundEvent");
    }
}
