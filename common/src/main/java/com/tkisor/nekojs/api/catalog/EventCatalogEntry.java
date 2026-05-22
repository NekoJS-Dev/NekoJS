package com.tkisor.nekojs.api.catalog;

import com.tkisor.nekojs.script.ScriptTypePredicate;

public record EventCatalogEntry(
        String group,
        String name,
        ScriptTypePredicate scriptType,
        Class<?> eventType,
        Class<?> dispatchKeyType,
        boolean cancellable,
        boolean dispatchable,
        String snippet
) {
    public static EventCatalogEntry of(
            String group,
            String name,
            ScriptTypePredicate scriptType,
            Class<?> eventType,
            Class<?> dispatchKeyType,
            boolean cancellable,
            boolean dispatchable
    ) {
        return new EventCatalogEntry(group, name, scriptType, eventType, dispatchKeyType, cancellable, dispatchable, group + "." + name + "(event => {\n  $0\n})");
    }
}
