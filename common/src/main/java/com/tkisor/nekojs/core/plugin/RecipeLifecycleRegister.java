package com.tkisor.nekojs.core.plugin;

import com.tkisor.nekojs.api.recipe.RecipeLifecycleContext;
import java.util.function.Consumer;

public interface RecipeLifecycleRegister {
    void beforeRecipeLoading(Consumer<RecipeLifecycleContext> hook);

    void afterRecipes(Consumer<RecipeLifecycleContext> hook);
}
