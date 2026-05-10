package com.tkisor.nekojs.api.catalog;

import com.tkisor.nekojs.script.ScriptType;

public record HostExtensionSource(
        Class<?> targetClass,
        Class<?> extensionInterface,
        ScriptType scriptType
) {
    public static HostExtensionSource common(Class<?> targetClass, Class<?> extensionInterface) {
        return new HostExtensionSource(targetClass, extensionInterface, ScriptType.COMMON);
    }
}
