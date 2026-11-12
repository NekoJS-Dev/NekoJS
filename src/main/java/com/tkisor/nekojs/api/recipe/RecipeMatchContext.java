package com.tkisor.nekojs.api.recipe;

import net.minecraft.world.item.crafting.RecipeHolder;

/** One immutable callback argument; it does not expose another runtime or mutable recipe builder. */
public final class RecipeMatchContext {
    private final RecipeMatchRecipe recipe;

    public RecipeMatchContext(RecipeHolder<?> holder) {
        recipe = new RecipeMatchRecipe(holder);
    }

    public RecipeMatchRecipe getRecipe() { return recipe; }
}
