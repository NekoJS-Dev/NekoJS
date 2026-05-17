package com.tkisor.nekojs.api.recipe;

import java.util.function.Consumer;

public interface RecipeLifecycleRegister {
    void beforeRecipeLoading(Consumer<RecipeLifecycleContext> hook);

    void afterRecipes(Consumer<RecipeLifecycleContext> hook);
}
