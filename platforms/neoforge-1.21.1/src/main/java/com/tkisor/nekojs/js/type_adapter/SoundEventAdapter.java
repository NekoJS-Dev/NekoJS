package com.tkisor.nekojs.js.type_adapter;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;

/**
 * {@link SoundEvent} 适配器：纯 id 查询，委托 {@link SimpleRegistryBasedAdapter}。
 * 支持 string id（自动补 {@code minecraft:}）/ {@code NekoId} / {@code Identifier} / 自身类型 host。
 */
public final class SoundEventAdapter extends SimpleRegistryBasedAdapter<SoundEvent> {
    public SoundEventAdapter() {
        super(BuiltInRegistries.SOUND_EVENT, SoundEvent.class, "SoundEvent");
    }
}
