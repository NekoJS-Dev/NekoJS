package com.tkisor.nekojs.api.catalog;

import com.tkisor.nekojs.script.ScriptType;

import java.lang.reflect.Method;

public record HostExtensionCatalogEntry(
        Class<?> targetClass,
        Class<?> extensionInterface,
        String javaName,
        String jsName,
        Method method,
        ScriptType scriptType,
        boolean hidden
) {
}
