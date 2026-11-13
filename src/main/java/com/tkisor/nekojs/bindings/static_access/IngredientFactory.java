package com.tkisor.nekojs.bindings.static_access;

import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.api.annotation.Return;
import com.tkisor.nekojs.wrapper.item.IngredientResolver;
import graal.graalvm.polyglot.Value;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * Script static binding {@code Ingredient}: factory for item ingredients.
 */
@Doc("Static binding 'Ingredient': factory helpers for item ingredients.")
public class IngredientFactory {
    /** Builds an ingredient that matches any of the given item values. */
    @Doc("Builds an ingredient matching any of the given item-like values.")
    @Doc("Accepts item ids and selector strings: '#tag', '@mod', '*' (any item), and '/regex/'.")
    @Param(name = "values", value = "item ids, '#tag'/'@mod'/'*'/'/regex/' strings, items, stacks, or ingredients")
    @Return("an IngredientJS combining all values with OR; empty wrapper when no value is given")
    public com.tkisor.nekojs.wrapper.item.IngredientJS of(Object... values) {
        com.tkisor.nekojs.wrapper.item.IngredientJS wrapper = new com.tkisor.nekojs.wrapper.item.IngredientJS();
        if (values != null) {
            for (Object value : values) {
                Ingredient ingredient = IngredientResolver.fromValue(toValue(value));
                wrapper.or(ingredient);
            }
        }
        return wrapper;
    }

    private static Value toValue(Object value) {
        return value == null ? null : Value.asValue(value);
    }

    /** An ingredient for a single item. */
    @Doc("Creates an ingredient matching a single item.")
    @Param(name = "id", value = "item id like 'minecraft:stick'")
    @Return("an IngredientJS matching only that item")
    public com.tkisor.nekojs.wrapper.item.IngredientJS item(String id) {
        return new com.tkisor.nekojs.wrapper.item.IngredientJS(id);
    }

    /** An item tag ingredient; the {@code '#'} prefix is added automatically when missing. */
    @Doc("Creates an ingredient matching an item tag.")
    @Param(name = "id", value = "tag id; a leading '#' is added automatically when missing")
    @Return("an IngredientJS matching all items in the tag")
    public com.tkisor.nekojs.wrapper.item.IngredientJS tag(String id) {
        return new com.tkisor.nekojs.wrapper.item.IngredientJS(id.startsWith("#") ? id : "#" + id);
    }

    /** Combines several ingredients with OR. */
    @Doc("Combines several ingredients into one that matches any of them.")
    @Param(name = "ingredients", value = "the ingredients to combine with OR")
    @Return("an IngredientJS matching any of the given ingredients")
    public com.tkisor.nekojs.wrapper.item.IngredientJS any(Ingredient... ingredients) {
        com.tkisor.nekojs.wrapper.item.IngredientJS wrapper = new com.tkisor.nekojs.wrapper.item.IngredientJS();
        for (Ingredient ingredient : ingredients) {
            wrapper.or(ingredient);
        }
        return wrapper;
    }

    /** A true wildcard that matches every registered item (a live any-holder set). */
    @Doc("Creates a true wildcard ingredient matching every registered item.")
    @Return("an IngredientJS backed by a live any-holder set, so items registered later still match")
    public com.tkisor.nekojs.wrapper.item.IngredientJS all() {
        return new com.tkisor.nekojs.wrapper.item.IngredientJS(IngredientResolver.wildcard());
    }

    /** Negates an ingredient: returns a DifferenceIngredient that matches every item except this one. */
    @Doc("Negates an ingredient: matches every item except the given one.")
    @Param(name = "ingredient", value = "the ingredient whose matches to exclude")
    @Return("an IngredientJS matching all items not matched by the given ingredient")
    public com.tkisor.nekojs.wrapper.item.IngredientJS not(Ingredient ingredient) {
        return new com.tkisor.nekojs.wrapper.item.IngredientJS(IngredientResolver.not(ingredient));
    }
}
