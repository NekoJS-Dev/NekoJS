package com.tkisor.nekojs.wrapper.item;

import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.api.annotation.Return;
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
@Doc("Script-facing ingredient wrapper combining alternatives with OR semantics.")
@Doc("1.12.2 has no compound/difference/intersection ingredient types; only OR-combinations are supported.")
public class IngredientJS implements NekoWrapper<Ingredient> {
    private final List<Ingredient> alternatives = new ArrayList<>();

    /** Creates an empty ingredient (matches nothing until an alternative is added). */
    public IngredientJS() {}

    /** Creates an ingredient from an id string (item id, 'ore:name', or '#tag' style). */
    @Doc("Creates an ingredient from an id string.")
    @Param(name = "id", value = "item id like 'minecraft:stone', or 'ore:planks' OreDictionary name")
    public IngredientJS(String id) {
        // Inline instead of delegating to or(): calling an instance method from a
        // constructor can hand a partially initialized `this` out (this-escape).
        this.alternatives.add(IngredientResolver.fromString(id));
    }

    /** Creates an ingredient wrapping a raw Ingredient. */
    @Doc("Creates an ingredient wrapping a raw Forge Ingredient.")
    @Param(name = "ingredient", value = "the ingredient to wrap; null and Ingredient.EMPTY are ignored")
    public IngredientJS(Ingredient ingredient) {
        if (ingredient != null && ingredient != Ingredient.EMPTY) {
            this.alternatives.add(ingredient);
        }
    }

    /** Adds an id-string alternative. */
    @Doc("Adds an alternative matched in addition to the existing ones (OR).")
    @Param(name = "id", value = "item id like 'minecraft:stone', or 'ore:planks' OreDictionary name")
    @Return("this wrapper, for chaining")
    public IngredientJS or(String id) {
        addAlternative(id);
        return this;
    }

    /** Adds a NekoId alternative. */
    @Doc("Adds an alternative from a NekoId.")
    @Param(name = "id", value = "the NekoId to match")
    @Return("this wrapper, for chaining")
    public IngredientJS or(NekoId id) {
        this.alternatives.add(IngredientResolver.fromNekoId(id));
        return this;
    }

    /** Adds an item alternative. */
    @Doc("Adds an item as an alternative.")
    @Param(name = "item", value = "the item to match")
    @Return("this wrapper, for chaining")
    public IngredientJS or(Item item) {
        this.alternatives.add(IngredientResolver.fromItem(item));
        return this;
    }

    /** Adds an item stack alternative. */
    @Doc("Adds an item stack as an alternative.")
    @Param(name = "stack", value = "the stack to match")
    @Return("this wrapper, for chaining")
    public IngredientJS or(ItemStack stack) {
        this.alternatives.add(IngredientResolver.fromStack(stack));
        return this;
    }

    /** Adds a raw ingredient alternative. */
    @Doc("Adds a raw Forge Ingredient as an alternative.")
    @Param(name = "ingredient", value = "the ingredient to add; null and Ingredient.EMPTY are ignored")
    @Return("this wrapper, for chaining")
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

    /** OR-combines another IngredientJS into this one. */
    @Doc("Adds all alternatives of another IngredientJS (OR).")
    @Param(name = "other", value = "the other ingredient wrapper")
    @Return("this wrapper, for chaining")
    public IngredientJS or(IngredientJS other) {
        return or(other.unwrap());
    }

    /** Tests whether a stack matches any alternative. */
    @Doc("Tests whether an item stack matches any alternative.")
    @Param(name = "stack", value = "the stack to test")
    @Return("true if the stack matches")
    public boolean matches(ItemStack stack) {
        return unwrap().apply(stack);
    }

    /** The first matching stack. */
    @Doc("Gets the first matching item stack.")
    @Return("the first stack, or the empty stack when there is none")
    public ItemStack first() {
        ItemStack[] stacks = unwrap().getMatchingStacks();
        return stacks.length > 0 ? stacks[0] : ItemStack.EMPTY;
    }

    /** Whether no alternatives have been added. */
    @Doc("Checks whether the ingredient has no alternatives.")
    @Return("true if nothing has been added yet")
    public boolean isEmpty() {
        return this.alternatives.isEmpty();
    }

    /** All matching stacks. */
    @Doc("Lists all matching item stacks.")
    @Return("array of matching stacks; empty when the ingredient is empty")
    public ItemStack[] getItems() {
        return unwrap().getMatchingStacks();
    }

    /** Resolves the wrapper into a single raw Ingredient. */
    @Doc("Resolves the wrapper into one raw Forge Ingredient.")
    @Return("the single alternative, an OR-combined Ingredient, or Ingredient.EMPTY when empty")
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
