package com.tkisor.nekojs.wrapper.event.registry;

import com.tkisor.nekojs.wrapper.registry.VillagerTypeBuilderJS;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.VillagerType;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * 村民类型注册事件（{@code StartupEvents.registry('villagerType')}）。
 */
public class VillagerTypeRegistryEventJS {

    private final RegisterEvent rawEvent;
    private final List<VillagerTypeBuilderJS> builders = new ArrayList<>();

    public VillagerTypeRegistryEventJS(RegisterEvent rawEvent) {
        this.rawEvent = rawEvent;
    }

    public VillagerTypeBuilderJS create(ResourceLocation id) {
        VillagerTypeBuilderJS builder = new VillagerTypeBuilderJS(id);
        builders.add(builder);
        return builder;
    }
    public void create(String id, java.util.function.Consumer<VillagerTypeBuilderJS> consumer) {
        VillagerTypeBuilderJS builder = create(ResourceLocation.parse(id));
        consumer.accept(builder);
    }

    public void registerAll() {
        for (VillagerTypeBuilderJS builder : builders) {
            rawEvent.register(Registries.VILLAGER_TYPE, builder.getLocation(), builder::create);
        }
    }
}
