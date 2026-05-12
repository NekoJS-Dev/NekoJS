package com.tkisor.nekojs.api.catalog;

import com.tkisor.nekojs.script.ScriptType;
import com.tkisor.nekojs.script.WithScriptType;

public record HostExtensionSource(
        Class<?> targetClass,
        Class<?> extensionInterface,
        ScriptType scriptType
) implements WithScriptType {
    public static HostExtensionSource common(Class<?> targetClass, Class<?> extensionInterface) {
        return new HostExtensionSource(targetClass, extensionInterface, ScriptType.COMMON);
    }
}
