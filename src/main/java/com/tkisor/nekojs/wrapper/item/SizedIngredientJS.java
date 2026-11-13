//? if neoforge {
package com.tkisor.nekojs.wrapper.item;

import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.api.annotation.Return;
import com.tkisor.nekojs.wrapper.NekoWrapper;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

/**
 * Item ingredient wrapper with a count (ingredient + count), for recipe inputs like "N of X".
 */
@Doc("An item ingredient paired with a count, for recipe inputs like '4 sticks'.")
public class SizedIngredientJS implements NekoWrapper<SizedIngredient> {
    private final Ingredient ingredient;
    private final int count;

    /** Constructor: the count must be positive (otherwise throws {@link IllegalArgumentException}). */
    @Doc("Creates a sized ingredient from an ingredient and a count.")
    @Param(name = "ingredient", value = "the ingredient to match")
    @Param(name = "count", value = "required item count; must be positive")
    public SizedIngredientJS(Ingredient ingredient, int count) {
        if (count <= 0) {
            throw new IllegalArgumentException("SizedIngredient count must be positive: " + count);
        }
        this.ingredient = ingredient;
        this.count = count;
    }

    /** The underlying ingredient. */
    @Doc("Gets the underlying ingredient.")
    @Return("the wrapped Ingredient")
    public Ingredient ingredient() {
        return ingredient;
    }

    /** The required count. */
    @Doc("Gets the required item count.")
    @Return("the count; always positive")
    public int count() {
        return count;
    }

    /** Unwraps to the vanilla {@link SizedIngredient} (a new instance on every call). */
    @Doc("Unwraps to the NeoForge SizedIngredient.")
    @Return("a new SizedIngredient instance built from this wrapper's ingredient and count")
    @Override
    public SizedIngredient unwrap() {
        return new SizedIngredient(ingredient, count);
    }
}
//?}
