package com.tkisor.nekojs.wrapper.event.registry;

import com.tkisor.nekojs.wrapper.registry.VillagerTypeBuilderJS;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.registry.VillagerRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 1.12.2 村民职业注册事件对象（{@code StartupEvents.registry('villagerType')}）。
 */
public class VillagerTypeRegistryEventJS {

    private final RegistryEvent.Register<VillagerRegistry.VillagerProfession> rawEvent;
    private final List<VillagerTypeBuilderJS> builders = new ArrayList<>();

    public VillagerTypeRegistryEventJS(RegistryEvent.Register<VillagerRegistry.VillagerProfession> rawEvent) {
        this.rawEvent = rawEvent;
    }

    public VillagerTypeBuilderJS create(String id) {
        VillagerTypeBuilderJS builder = new VillagerTypeBuilderJS(id);
        builders.add(builder);
        return builder;
    }

    public void create(String id, Consumer<VillagerTypeBuilderJS> consumer) {
        VillagerTypeBuilderJS builder = create(id);
        consumer.accept(builder);
    }

    public void registerAll() {
        for (VillagerTypeBuilderJS builder : builders) {
            rawEvent.getRegistry().register(builder.build());
        }
        builders.clear();
    }
}
