package com.tkisor.nekojs.api.probe;

import com.tkisor.nekojs.script.ScriptType;

public record ProbeExtensionDoc(
        Class<?> targetClass,
        Class<?> extensionInterface,
        String javaName,
        String jsName,
        ScriptType scriptType,
        boolean hidden
) {
}
