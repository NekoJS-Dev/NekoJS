package com.tkisor.nekojs.api.recipe;

import com.tkisor.nekojs.api.NekoJSPlugin;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Experimental
public interface RecipeLifecyclePlugin extends NekoJSPlugin {
    @Override
    default void registerRecipeLifecycleHooks(RecipeLifecycleRegister registry) {
        registry.beforeRecipeLoading(this::beforeRecipeLoading);
        registry.afterRecipes(this::afterRecipes);
    }

    default void beforeRecipeLoading(RecipeLifecycleContext context) {}

    default void afterRecipes(RecipeLifecycleContext context) {}
}
