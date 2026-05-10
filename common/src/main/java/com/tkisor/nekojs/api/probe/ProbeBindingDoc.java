package com.tkisor.nekojs.api.probe;

import com.tkisor.nekojs.script.ScriptType;

public record ProbeBindingDoc(
        String name,
        ScriptType scriptType,
        Class<?> javaType,
        boolean staticClass,
        boolean hostClass,
        boolean emit,
        String typeOverride,
        String description
) {
    public static ProbeBindingDoc of(String name, ScriptType scriptType, Class<?> javaType, boolean staticClass) {
        return new ProbeBindingDoc(name, scriptType, javaType, staticClass, staticClass, true, null, null);
    }
}
