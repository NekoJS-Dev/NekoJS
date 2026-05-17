package com.tkisor.nekojs.api.recipe;

@FunctionalInterface
public interface RecipeNamespaceRegister {
    <C> void register(RecipeNamespaceEntry<C> entry);
}
