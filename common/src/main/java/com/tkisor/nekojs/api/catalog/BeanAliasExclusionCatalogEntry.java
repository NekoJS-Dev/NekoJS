package com.tkisor.nekojs.api.catalog;

import java.util.Objects;

/**
 * Omits one derived TypeScript Bean property on exactly the target Java class.
 * Real accessors, other classes and shared reflection metadata remain unchanged.
 */
public record BeanAliasExclusionCatalogEntry(Class<?> targetType, String getterName, String propertyName) {
    public BeanAliasExclusionCatalogEntry {
        Objects.requireNonNull(targetType, "[NEKO-4047] Bean alias exclusion rejected: targetType is required");
        if (targetType.isPrimitive() || targetType.isArray() || targetType.isLocalClass()
                || targetType.getSimpleName().isEmpty() || targetType.getPackageName().isEmpty()
                || getterName == null || getterName.isBlank() || propertyName == null || propertyName.isBlank()) {
            throw new IllegalArgumentException("[NEKO-4047] Bean alias exclusion rejected: "
                    + "a named Java type, getter name and property name are required");
        }
    }
}
