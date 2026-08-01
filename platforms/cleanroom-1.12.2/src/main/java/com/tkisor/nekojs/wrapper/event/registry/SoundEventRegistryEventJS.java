package com.tkisor.nekojs.wrapper.event.registry;

import com.tkisor.nekojs.wrapper.registry.SoundEventBuilderJS;
import net.minecraft.util.SoundEvent;
import net.minecraftforge.event.RegistryEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 1.12.2 声音注册事件对象（{@code StartupEvents.registry('soundEvent')}）。
 */
public class SoundEventRegistryEventJS {

    private final RegistryEvent.Register<SoundEvent> rawEvent;
    private final List<SoundEventBuilderJS> builders = new ArrayList<>();

    public SoundEventRegistryEventJS(RegistryEvent.Register<SoundEvent> rawEvent) {
        this.rawEvent = rawEvent;
    }

    public SoundEventBuilderJS create(String id) {
        SoundEventBuilderJS builder = new SoundEventBuilderJS(id);
        builders.add(builder);
        return builder;
    }

    public void create(String id, Consumer<SoundEventBuilderJS> consumer) {
        SoundEventBuilderJS builder = create(id);
        consumer.accept(builder);
    }

    public void registerAll() {
        for (SoundEventBuilderJS builder : builders) {
            rawEvent.getRegistry().register(builder.build());
        }
        builders.clear();
    }
}
