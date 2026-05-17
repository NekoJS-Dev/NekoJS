package com.tkisor.nekojs.api.catalog;

import java.util.List;

public record RecipeNamespaceCatalogEntry(
        String namespace,
        Class<?> handlerClass,
        List<String> recipeTypes,
        boolean fallbackSupported,
        List<String> examples
) {
    public RecipeNamespaceCatalogEntry {
        recipeTypes = List.copyOf(recipeTypes == null ? List.of() : recipeTypes);
        examples = List.copyOf(examples == null ? List.of() : examples);
    }

    public RecipeNamespaceCatalogEntry(String namespace, Class<?> handlerClass, boolean fallbackSupported, List<String> examples) {
        this(namespace, handlerClass, List.of(), fallbackSupported, examples);
    }

    public static RecipeNamespaceCatalogEntry of(String namespace, Class<?> handlerClass) {
        return new RecipeNamespaceCatalogEntry(namespace, handlerClass, List.of(), true, List.of());
    }
}
