package com.tkisor.nekojs.wrapper.item;

import com.tkisor.nekojs.api.data.NekoId;
import com.tkisor.nekojs.wrapper.NekoWrapper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.crafting.CompoundIngredient;
import net.neoforged.neoforge.common.crafting.DifferenceIngredient;
import net.neoforged.neoforge.common.crafting.IntersectionIngredient;

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
        return or(IngredientResolver.fromStack(stack));
    }

    public IngredientJS or(Ingredient ingredient) {
        Ingredient resolved = IngredientResolver.fromIngredient(ingredient);
        if (resolved != null && !resolved.isEmpty()) this.alternatives.add(resolved);
        return this;
    }

    public IngredientJS or(IngredientJS other) {
        return or(other.unwrap());
    }

    public IngredientJS and(Ingredient ingredient) {
        return new IngredientJS(IntersectionIngredient.of(unwrap(), ingredient));
    }

    public IngredientJS intersect(Ingredient ingredient) {
        return and(ingredient);
    }

    public IngredientJS except(Ingredient ingredient) {
        return new IngredientJS(DifferenceIngredient.of(unwrap(), ingredient));
    }

    public IngredientJS subtract(Ingredient ingredient) {
        return except(ingredient);
    }

    public IngredientJS asIngredient() {
        return this;
    }

    public SizedIngredientJS asStack() {
        return withCount(1);
    }

    public SizedIngredientJS withCount(int count) {
        return new SizedIngredientJS(unwrap(), count);
    }

    public boolean matches(ItemStack stack) {
        return unwrap().test(stack);
    }

    public boolean test(ItemStack stack) {
        return matches(stack);
    }

    public boolean matches(Ingredient ingredient) {
        for (ItemStack stack : stacks()) {
            if (ingredient.test(stack)) return true;
        }
        return false;
    }

    public ItemStack first() {
        List<ItemStack> stacks = stacks();
        return stacks.isEmpty() ? ItemStack.EMPTY : stacks.getFirst();
    }

    public List<ItemStack> stacks() {
        return List.of(unwrap().getItems());
    }

    public List<ItemStack> displayStacks() {
        return stacks();
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
