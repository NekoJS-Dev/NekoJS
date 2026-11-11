package dev.latvian.mods.kubejs.item;

import dev.latvian.mods.kubejs.registry.BuilderBase;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

/** Native item construction only; registry ownership remains with the caller. */
public class ItemBuilder extends BuilderBase<Item> {
    public ItemBuilder(ResourceLocation id) { super(id); }
    protected Item.Properties createItemProperties() { return new Item.Properties(); }
    @Override public Item createObject() { return new Item(createItemProperties()); }
}
