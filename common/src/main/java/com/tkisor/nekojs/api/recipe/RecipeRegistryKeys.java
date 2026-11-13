package com.tkisor.nekojs.api.recipe;

import java.util.List;

/**
 * Key constants for the {@code event.recipes} proxy, shared by the three neoforge platforms so
 * they cannot drift apart. Each neoforge platform's {@code RecipeRegistryProxy} exposes its
 * introspection methods under these keys.
 */
public final class RecipeRegistryKeys {
    public static final String NAMESPACES = "namespaces";
    public static final String TYPES = "types";
    public static final String HAS_NAMESPACE = "hasNamespace";
    public static final String HAS_TYPE = "hasType";
    public static final String DESCRIBE = "describeType";
    public static final List<String> HELPER_KEYS = List.of(NAMESPACES, TYPES, HAS_NAMESPACE, HAS_TYPE, DESCRIBE);

    private RecipeRegistryKeys() {}
}
