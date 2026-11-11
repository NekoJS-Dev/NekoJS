package dev.latvian.mods.kubejs.error;

import dev.latvian.mods.kubejs.recipe.component.RecipeComponent;

/** Preserves original component validation failures and their source attachment. */
public class InvalidRecipeComponentValueException extends KubeRuntimeException {
    public InvalidRecipeComponentValueException(String message, RecipeComponent<?> component, Object value) {
        super("[NEKO-5336] " + message + "; component=" + component + ", value=" + value);
    }
}
