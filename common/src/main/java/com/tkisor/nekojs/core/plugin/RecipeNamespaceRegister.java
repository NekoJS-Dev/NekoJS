package com.tkisor.nekojs.core.plugin;

import com.tkisor.nekojs.api.recipe.RecipeNamespaceEntry;

@FunctionalInterface
public interface RecipeNamespaceRegister {
    /** Register a Java handler class for a namespace. */
    <C> void register(RecipeNamespaceEntry entry);
}
