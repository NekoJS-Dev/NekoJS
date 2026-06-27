package com.tkisor.nekojs.wrapper.registry;

import lombok.Getter;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;

import java.util.function.Consumer;

public class ItemBuilderJS {
    @Getter
    private final Identifier location;

    private int maxStackSize = 64;
    private int maxDamage = 0;
    private boolean fireResistant = false;
    private Rarity rarity = Rarity.COMMON;
    private boolean glowing = false;

    private FoodBuilderJS foodBuilder = null;

    public ItemBuilderJS(Identifier location) {
        this.location = location;
    }

    public ItemBuilderJS maxStackSize(int size) { this.maxStackSize = size; return this; }
    public ItemBuilderJS maxDamage(int damage) { this.maxDamage = damage; return this; }
    public ItemBuilderJS fireResistant() { this.fireResistant = true; return this; }

    public ItemBuilderJS rarity(String rarityStr) {
        this.rarity = switch (rarityStr.toLowerCase()) {
            case "uncommon" -> Rarity.UNCOMMON;
            case "rare" -> Rarity.RARE;
            case "epic" -> Rarity.EPIC;
            default -> Rarity.COMMON;
        };
        return this;
    }

    public ItemBuilderJS glowing() { this.glowing = true; return this; }

    public ItemBuilderJS food(Consumer<FoodBuilderJS> consumer) {
        this.foodBuilder = new FoodBuilderJS();
        consumer.accept(this.foodBuilder);
        return this;
    }

    public Item createItem() {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, location);
        Item.Properties props = new Item.Properties().setId(key);

        if (maxDamage > 0) {
            props.durability(maxDamage);
        } else {
            props.stacksTo(maxStackSize);
        }

        if (fireResistant) props.fireResistant();
        if (rarity != Rarity.COMMON) props.rarity(rarity);

        if (foodBuilder != null) {
            props.food(foodBuilder.buildFood());

            props.component(DataComponents.CONSUMABLE, foodBuilder.buildConsumable());
        }

        if (glowing) {
            return new Item(props) {
                @Override
                public boolean isFoil(ItemStack stack) {
                    return true;
                }
            };
        }

        return new Item(props);
    }
}