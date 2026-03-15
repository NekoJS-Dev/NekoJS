package com.tkisor.nekojs.wrapper.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

public class ItemBuilderJS {
    private final String id;
    private int maxStackSize = 64; // 默认堆叠 64

    public ItemBuilderJS(String id) {
        this.id = id;
    }

    public ItemBuilderJS maxStackSize(int size) {
        this.maxStackSize = size;
        return this;
    }

    public Item createItem(Identifier location) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, location);

        Item.Properties props = new Item.Properties()
                .setId(key)
                .stacksTo(maxStackSize);

        return new Item(props);
    }
}