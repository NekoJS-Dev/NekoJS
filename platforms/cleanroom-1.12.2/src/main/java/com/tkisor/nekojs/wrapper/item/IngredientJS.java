package com.tkisor.nekojs.wrapper.item;

import com.tkisor.nekojs.api.data.NekoId;
import com.tkisor.nekojs.wrapper.NekoWrapper;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.Ingredient;

import java.util.ArrayList;
import java.util.List;

/**
 * 1.12.2 simplified IngredientJS - no CompoundIngredient/DifferenceIngredient/IntersectionIngredient
 */
public class IngredientJS implements NekoWrapper<Ingredient> {
    private final List<Ingredient> alternatives = new ArrayList<>();

    public IngredientJS() {}

    public IngredientJS(String id) {
        // Inline instead of delegating to or(): calling an instance method from a
        // constructor can hand a partially initialized `this` out (this-escape).
        this.alternatives.add(IngredientResolver.fromString(id));
    }

    public IngredientJS(Ingredient ingredient) {
        if (ingredient != null && ingredient != Ingredient.EMPTY) {
            this.alternatives.add(ingredient);
        }
    }

    public IngredientJS or(String id) {
        addAlternative(id);
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
        addAlternative(ingredient);
        return this;
    }

    private void addAlternative(String id) {
        this.alternatives.add(IngredientResolver.fromString(id));
    }

    private void addAlternative(Ingredient ingredient) {
        if (ingredient != null && ingredient != Ingredient.EMPTY) {
            this.alternatives.add(ingredient);
        }
    }

    public IngredientJS or(IngredientJS other) {
        return or(other.unwrap());
    }

    public boolean matches(ItemStack stack) {
        return unwrap().apply(stack);
    }

    public ItemStack first() {
        ItemStack[] stacks = unwrap().getMatchingStacks();
        return stacks.length > 0 ? stacks[0] : ItemStack.EMPTY;
    }

    public boolean isEmpty() {
        return this.alternatives.isEmpty();
    }

    public ItemStack[] getItems() {
        return unwrap().getMatchingStacks();
    }

    @Override
    public Ingredient unwrap() {
        if (alternatives.isEmpty()) return Ingredient.EMPTY;
        if (alternatives.size() == 1) return alternatives.get(0);
        // Combine all alternatives
        List<ItemStack> stacks = new ArrayList<>();
        for (Ingredient ing : alternatives) {
            for (ItemStack stack : ing.getMatchingStacks()) {
                stacks.add(stack.copy());
            }
        }
        return stacks.isEmpty() ? Ingredient.EMPTY : Ingredient.fromStacks(stacks.toArray(new ItemStack[0]));
    }
}
