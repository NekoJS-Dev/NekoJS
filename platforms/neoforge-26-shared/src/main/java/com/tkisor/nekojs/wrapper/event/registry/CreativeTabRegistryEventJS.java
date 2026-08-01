package com.tkisor.nekojs.wrapper.event.registry;

import com.tkisor.nekojs.wrapper.registry.CreativeTabBuilderJS;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * 创造模式标签页注册事件对象（{@code StartupEvents.registry('creativeModeTab')}）。
 */
public class CreativeTabRegistryEventJS {

    private final RegisterEvent rawEvent;
    private final List<CreativeTabBuilderJS> builders = new ArrayList<>();

    public CreativeTabRegistryEventJS(RegisterEvent rawEvent) {
        this.rawEvent = rawEvent;
    }

    public CreativeTabBuilderJS create(Identifier id) {
        CreativeTabBuilderJS builder = new CreativeTabBuilderJS(id);
        builders.add(builder);
        return builder;
    }
    public void create(String id, java.util.function.Consumer<CreativeTabBuilderJS> consumer) {
        CreativeTabBuilderJS builder = create(Identifier.parse(id));
        consumer.accept(builder);
    }

    public void registerAll() {
        for (CreativeTabBuilderJS builder : builders) {
            rawEvent.register(Registries.CREATIVE_MODE_TAB, builder.getLocation(), builder::build);
        }
    }
}
