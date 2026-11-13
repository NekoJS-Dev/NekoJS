package dev.latvian.mods.kubejs.recipe.filter;

import dev.latvian.mods.rhino.Context;

/** Native context accessor; no recipe filtering engine is installed. */
public interface RecipeMatchContext {
    Context cx();
}
