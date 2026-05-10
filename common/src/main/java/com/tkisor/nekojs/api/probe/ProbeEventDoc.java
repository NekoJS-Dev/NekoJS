package com.tkisor.nekojs.api.probe;

import com.tkisor.nekojs.script.ScriptType;

public record ProbeEventDoc(
        String group,
        String name,
        ScriptType scriptType,
        Class<?> eventType,
        Class<?> dispatchKeyType,
        boolean cancellable,
        boolean dispatchable,
        String snippet
) {
    public static ProbeEventDoc of(
            String group,
            String name,
            ScriptType scriptType,
            Class<?> eventType,
            Class<?> dispatchKeyType,
            boolean cancellable,
            boolean dispatchable
    ) {
        return new ProbeEventDoc(group, name, scriptType, eventType, dispatchKeyType, cancellable, dispatchable, null);
    }
}
