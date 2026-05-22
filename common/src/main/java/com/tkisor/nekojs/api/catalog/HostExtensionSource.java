package com.tkisor.nekojs.api.catalog;

import com.tkisor.nekojs.script.ScriptTypePredicate;
import com.tkisor.nekojs.script.WithScriptType;

public record HostExtensionSource(
        Class<?> targetClass,
        Class<?> extensionInterface,
        ScriptTypePredicate scriptType
) implements WithScriptType {
    public static HostExtensionSource any(Class<?> targetClass, Class<?> extensionInterface) {
        return new HostExtensionSource(targetClass, extensionInterface, ScriptTypePredicate.any());
    }
}
