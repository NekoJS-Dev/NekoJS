package dev.latvian.mods.kubejs.recipe.match;

import dev.latvian.mods.kubejs.recipe.filter.RecipeMatchContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

public interface ItemMatch extends ReplacementMatch {
    boolean matches(RecipeMatchContext cx, ItemStack item, boolean exact);
    boolean matches(RecipeMatchContext cx, Ingredient ingredient, boolean exact);
}
