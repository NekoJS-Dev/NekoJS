package com.tkisor.nekojs.api.recipe;

@FunctionalInterface
public interface RecipeNamespaceRegister {
    /** Register a Java handler class for a namespace. */
    <C> void register(RecipeNamespaceEntry<C> entry);
}
