package com.tkisor.nekojs.api.recipe;

import java.util.Map;
import java.util.Set;

/**
 * JSON recipe type catalog discovered by scanning mod jars' {@code assets/<modid>/recipes/*.json}.
 * Informational only (no schema fields): exposed to scripts via {@code RecipeSchema.jsonTypes(ns)}
 * so pack authors can see which custom recipe types a mod declares without implying builders exist.
 */
public final class RecipeJsonTypeCatalog {
    private static volatile Map<String, Set<String>> CATALOG = Map.of();

    private RecipeJsonTypeCatalog() {}

    public static void setCatalog(Map<String, Set<String>> catalog) {
        CATALOG = catalog == null ? Map.of() : catalog;
    }

    /** Recipe types declared in JSON recipes of the given namespace (empty set when unknown). */
    public static Set<String> types(String namespace) {
        return CATALOG.getOrDefault(namespace, Set.of());
    }
}
