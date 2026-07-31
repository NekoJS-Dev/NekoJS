package com.tkisor.nekojs.wrapper.event.registry;

import com.tkisor.nekojs.wrapper.registry.PotionBuilderJS;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.alchemy.Potion;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * 药水注册事件（{@code StartupEvents.registry('potion')}）。
 */
public class PotionRegistryEventJS {

    private final RegisterEvent rawEvent;
    private final List<PotionBuilderJS> builders = new ArrayList<>();

    public PotionRegistryEventJS(RegisterEvent rawEvent) {
        this.rawEvent = rawEvent;
    }

    public PotionBuilderJS create(ResourceLocation id) {
        PotionBuilderJS builder = new PotionBuilderJS(id);
        builders.add(builder);
        return builder;
    }

    public void registerAll() {
        for (PotionBuilderJS builder : builders) {
            rawEvent.register(Registries.POTION, builder.getLocation(), builder::create);
        }
    }
}
