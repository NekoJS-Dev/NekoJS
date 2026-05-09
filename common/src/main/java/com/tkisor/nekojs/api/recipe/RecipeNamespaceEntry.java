package com.tkisor.nekojs.api.recipe;

import java.util.function.Function;

/**
 * Unified recipe namespace registration entry.
 *
 * @param namespace    the namespace (e.g. "minecraft")
 * @param factory      handler constructor factory (e.g. {@code Handler::new})
 * @param handlerClass handler Java type (e.g. {@code Handler.class}), for external mods to query
 */
public record RecipeNamespaceEntry<C>(
        String namespace,
        Function<C, Object> factory,
        Class<?> handlerClass
) {
    public static <C> RecipeNamespaceEntry<C> of(String namespace,
                                                 Function<C, Object> factory,
                                                 Class<?> handlerClass) {
        return new RecipeNamespaceEntry<>(namespace, factory, handlerClass);
    }
}
