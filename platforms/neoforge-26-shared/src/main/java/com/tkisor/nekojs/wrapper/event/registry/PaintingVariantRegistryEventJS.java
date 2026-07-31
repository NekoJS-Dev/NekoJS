package com.tkisor.nekojs.wrapper.event.registry;

import com.tkisor.nekojs.wrapper.registry.PaintingVariantBuilderJS;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.decoration.painting.PaintingVariant;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * 画变种注册事件（{@code StartupEvents.registry('paintingVariant')}）。
 */
public class PaintingVariantRegistryEventJS {

    private final RegisterEvent rawEvent;
    private final List<PaintingVariantBuilderJS> builders = new ArrayList<>();

    public PaintingVariantRegistryEventJS(RegisterEvent rawEvent) {
        this.rawEvent = rawEvent;
    }

    public PaintingVariantBuilderJS create(Identifier id) {
        PaintingVariantBuilderJS builder = new PaintingVariantBuilderJS(id);
        builders.add(builder);
        return builder;
    }

    public void registerAll() {
        for (PaintingVariantBuilderJS builder : builders) {
            rawEvent.register(Registries.PAINTING_VARIANT, builder.getLocation(), builder::create);
        }
    }
}
