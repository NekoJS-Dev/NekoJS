package com.tkisor.nekojs.wrapper.item;

import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.api.annotation.Return;
import net.minecraft.item.crafting.Ingredient;

/**
 * 1.12.2 SizedIngredientJS - simple wrapper holding an Ingredient + count.
 * 1.12.2 has no SizedIngredient class, so this is a pure wrapper.
 */
@Doc("An ingredient paired with a count (1.12.2 has no native SizedIngredient).")
public class SizedIngredientJS {
    private final Ingredient ingredient;
    private final int count;

    /** Creates a sized ingredient; count must be positive. */
    @Doc("Creates a sized ingredient.")
    @Param(name = "ingredient", value = "the wrapped ingredient")
    @Param(name = "count", value = "the required amount; must be positive")
    public SizedIngredientJS(Ingredient ingredient, int count) {
        if (count <= 0) {
            throw new IllegalArgumentException("SizedIngredient count must be positive: " + count);
        }
        this.ingredient = ingredient;
        this.count = count;
    }

    /** The wrapped ingredient. */
    @Doc("Gets the wrapped ingredient.")
    @Return("the underlying Ingredient")
    public Ingredient ingredient() {
        return ingredient;
    }

    /** The required count. */
    @Doc("Gets the required amount.")
    @Return("the positive item count")
    public int count() {
        return count;
    }

    /** Alias of ingredient(). */
    @Doc("Unwraps the underlying ingredient.")
    @Return("the underlying Ingredient")
    public Ingredient unwrap() {
        return ingredient;
    }
}
