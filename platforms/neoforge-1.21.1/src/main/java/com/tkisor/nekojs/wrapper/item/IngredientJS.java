package com.tkisor.nekojs.wrapper.item;

import com.tkisor.nekojs.api.data.NekoId;
import com.tkisor.nekojs.wrapper.NekoWrapper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.ArrayList;
import java.util.List;

public class IngredientJS implements NekoWrapper<Ingredient> {
    private final List<Ingredient> alternatives = new ArrayList<>();

    public IngredientJS() {}

    public IngredientJS(String... ids) {
        for (String id : ids) {
            or(id);
        }
    }

    public IngredientJS(Ingredient ingredient) {
        or(ingredient);
    }

    public IngredientJS or(String id) {
        this.alternatives.add(IngredientResolver.fromString(id));
        return this;
    }

    public IngredientJS or(NekoId id) {
        this.alternatives.add(IngredientResolver.fromNekoId(id));
        return this;
    }

    public IngredientJS or(Item item) {
        this.alternatives.add(IngredientResolver.fromItem(item));
        return this;
    }

    public IngredientJS or(ItemStack stack) {
        this.alternatives.add(IngredientResolver.fromStack(stack));
        return this;
    }

    public IngredientJS or(Ingredient ingredient) {
        this.alternatives.add(IngredientResolver.fromIngredient(ingredient));
        return this;
    }

    public IngredientJS or(IngredientJS other) {
        this.alternatives.add(other.unwrap());
        return this;
    }

    public boolean isEmpty() {
        return this.alternatives.isEmpty();
    }

    public ItemStack[] getItems() {
        return unwrap().getItems();
    }

    @Override
    public Ingredient unwrap() {
        return IngredientResolver.combine(alternatives);
    }
}
