package com.tkisor.nekojs.wrapper.item;

import com.tkisor.nekojs.wrapper.NekoWrapper;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

public class SizedIngredientJS implements NekoWrapper<SizedIngredient> {
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

    @Override
    public SizedIngredient unwrap() {
        return new SizedIngredient(ingredient, count);
    }
}
