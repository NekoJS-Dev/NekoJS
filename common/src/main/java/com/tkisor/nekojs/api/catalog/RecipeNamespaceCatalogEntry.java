package com.tkisor.nekojs.api.catalog;

import java.util.List;

public record RecipeNamespaceCatalogEntry(
        String namespace,
        Class<?> handlerClass,
        boolean fallbackSupported,
        List<String> examples
) {
    public static RecipeNamespaceCatalogEntry of(String namespace, Class<?> handlerClass) {
        return new RecipeNamespaceCatalogEntry(namespace, handlerClass, true, List.of());
    }
}
