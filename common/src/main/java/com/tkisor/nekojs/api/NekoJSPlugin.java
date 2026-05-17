package com.tkisor.nekojs.api;

import com.tkisor.nekojs.api.event.EventGroupRegistry;
import com.tkisor.nekojs.api.recipe.RecipeNamespaceRegister;
import org.jetbrains.annotations.ApiStatus;

/**
 * NekoJS 平台插件接口。
 */
@ApiStatus.Experimental
public interface NekoJSPlugin extends NekoJSBasePlugin {
    default void registerEvents(EventGroupRegistry registry) {}

    default void registerClientEvents(EventGroupRegistry registry) {}

    default void registerRecipeNamespaces(RecipeNamespaceRegister registry) {}
}
