package com.tkisor.nekojs.wrapper.event.registry;

import com.tkisor.nekojs.wrapper.registry.SoundEventBuilderJS;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * 声音事件注册事件（{@code StartupEvents.registry('soundEvent')}）。
 */
public class SoundEventRegistryEventJS {

    private final RegisterEvent rawEvent;
    private final List<SoundEventBuilderJS> builders = new ArrayList<>();

    public SoundEventRegistryEventJS(RegisterEvent rawEvent) {
        this.rawEvent = rawEvent;
    }

    public SoundEventBuilderJS create(Identifier id) {
        SoundEventBuilderJS builder = new SoundEventBuilderJS(id);
        builders.add(builder);
        return builder;
    }

    public void registerAll() {
        for (SoundEventBuilderJS builder : builders) {
            rawEvent.register(Registries.SOUND_EVENT, builder.getLocation(), builder::create);
        }
    }
}
