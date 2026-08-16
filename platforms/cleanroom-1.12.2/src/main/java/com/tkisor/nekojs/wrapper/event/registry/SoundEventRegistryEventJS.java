package com.tkisor.nekojs.wrapper.event.registry;

import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.api.annotation.Return;
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

    /** 包装原始 Forge 注册事件。 */
    public SoundEventRegistryEventJS(RegistryEvent.Register<SoundEvent> rawEvent) {
        this.rawEvent = rawEvent;
    }

    /** 创建一个声音事件 builder。 */
    @Doc("Creates a new sound event builder.")
    @Param(name = "id", value = "sound id like 'mymod:my_sound', pointing at a sounds.json entry")
    @Return("a new SoundEventBuilderJS for chaining; the sound event is registered when the event completes")
    public SoundEventBuilderJS create(String id) {
        SoundEventBuilderJS builder = new SoundEventBuilderJS(id);
        builders.add(builder);
        return builder;
    }

    /** 创建并一步配置声音事件 builder。 */
    @Doc("Creates a new sound event builder and configures it in one call.")
    @Param(name = "id", value = "sound id like 'mymod:my_sound'")
    @Param(name = "consumer", value = "callback receiving the builder for configuration")
    public void create(String id, Consumer<SoundEventBuilderJS> consumer) {
        SoundEventBuilderJS builder = create(id);
        consumer.accept(builder);
    }

    /** 注册全部声音事件。 */
    @Doc("Registers all sound events created in this event into the Forge registry.")
    @Doc("Called automatically when the event completes; manual calls are normally unnecessary.")
    public void registerAll() {
        for (SoundEventBuilderJS builder : builders) {
            rawEvent.getRegistry().register(builder.build());
        }
        builders.clear();
    }
}
