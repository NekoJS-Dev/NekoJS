package com.tkisor.nekojs.api.recipe;

import java.util.function.Function;

/**
 * Recipe namespace handler registration entry.
 *
 * @param namespace    the namespace (e.g. "minecraft")
 * @param factory      handler constructor (e.g. {@code event -> new Handler(event)})
 * @param handlerClass handler type for reflection-based introspection
 */
public record RecipeNamespaceEntry(
        String namespace,
        Function<Object, Object> factory,
        Class<?> handlerClass
) {
    public static RecipeNamespaceEntry of(String namespace, Function<Object, Object> factory, Class<?> handlerClass) {
        return new RecipeNamespaceEntry(namespace, factory, handlerClass);
    }
}
