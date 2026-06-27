package com.tkisor.nekojs.wrapper.event.registry;

import com.tkisor.nekojs.wrapper.registry.ItemBuilderJS;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class ItemRegistryEventJS  {

    private final RegisterEvent rawEvent;

    private final List<ItemBuilderJS> builders = new ArrayList<>();
    private final Map<Identifier, Supplier<Item>> customItems = new HashMap<>();

    public ItemRegistryEventJS(RegisterEvent rawEvent) {
        this.rawEvent = rawEvent;
    }

    public ItemBuilderJS create(Identifier id) {
        ItemBuilderJS builder = new ItemBuilderJS(id);

        builders.add(builder);
        return builder;
    }

    public void createCustom(Identifier id, Supplier<Item> itemSupplier) {
        customItems.put(id, itemSupplier);
    }

    public void registerAll() {
        for (ItemBuilderJS builder : builders) {
            rawEvent.register(Registries.ITEM, builder.getLocation(), builder::createItem);
        }

        customItems.forEach((location, supplier) -> {
            rawEvent.register(Registries.ITEM, location, supplier);
        });
    }
}