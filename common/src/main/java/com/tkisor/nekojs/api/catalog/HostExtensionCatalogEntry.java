package com.tkisor.nekojs.api.catalog;

import com.tkisor.nekojs.script.ScriptTypePredicate;

import java.lang.reflect.Method;

public record HostExtensionCatalogEntry(
        Class<?> targetClass,
        Class<?> extensionInterface,
        String javaName,
        String jsName,
        Method method,
        ScriptTypePredicate scriptType,
        boolean hidden
) {
}
