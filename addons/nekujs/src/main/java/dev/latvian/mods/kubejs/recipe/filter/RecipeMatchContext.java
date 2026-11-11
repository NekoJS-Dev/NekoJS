package dev.latvian.mods.kubejs.recipe.filter;

import dev.latvian.mods.rhino.Context;

/** Native context seam; no recipe filtering engine is installed. */
public interface RecipeMatchContext {
    Context cx();
}
