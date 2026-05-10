package com.tkisor.nekojs.api.catalog;

import com.tkisor.nekojs.script.ScriptType;

public record BindingCatalogEntry(
        String name,
        ScriptType scriptType,
        Class<?> javaType,
        boolean staticClass,
        boolean hostClass,
        boolean emit,
        String typeOverride,
        String description
) {
    public static BindingCatalogEntry of(String name, ScriptType scriptType, Class<?> javaType, boolean staticClass) {
        return new BindingCatalogEntry(name, scriptType, javaType, staticClass, staticClass, true, null, null);
    }
}
