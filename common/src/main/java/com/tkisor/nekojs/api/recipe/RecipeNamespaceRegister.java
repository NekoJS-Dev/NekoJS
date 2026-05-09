package com.tkisor.nekojs.api.recipe;

@FunctionalInterface
public interface RecipeNamespaceRegister<C> {
    /**
     * Registers a recipe namespace handler.
     */
    void register(RecipeNamespaceEntry<C> entry);
}
