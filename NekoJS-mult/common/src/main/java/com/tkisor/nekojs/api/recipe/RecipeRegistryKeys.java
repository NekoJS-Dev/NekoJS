package com.tkisor.nekojs.api.recipe;

import java.util.List;

/**
 * {@code event.recipes} proxy 的 key 常量（neoforge 平台共享，避免三份漂移）。
 * 三个 neoforge 平台的 {@code RecipeRegistryProxy} 用这些 key 暴露内省方法。
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
