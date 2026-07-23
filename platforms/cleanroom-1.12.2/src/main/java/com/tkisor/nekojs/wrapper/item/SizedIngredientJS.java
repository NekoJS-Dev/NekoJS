package com.tkisor.nekojs.wrapper.item;

import net.minecraft.item.crafting.Ingredient;

/**
 * 1.12.2 SizedIngredientJS - simple wrapper holding an Ingredient + count.
 * 1.12.2 has no SizedIngredient class, so this is a pure wrapper.
 */
public class SizedIngredientJS {
    private final Ingredient ingredient;
    private final int count;

    public SizedIngredientJS(Ingredient ingredient, int count) {
        if (count <= 0) {
            throw new IllegalArgumentException("SizedIngredient count must be positive: " + count);
        }
        this.ingredient = ingredient;
        this.count = count;
    }

    public Ingredient ingredient() {
        return ingredient;
    }

    public int count() {
        return count;
    }

    public Ingredient unwrap() {
        return ingredient;
    }
}
