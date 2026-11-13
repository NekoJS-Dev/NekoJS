package com.tkisor.nekojs.api.catalog;

import com.tkisor.nekojs.api.ScriptTypePredicate;

import java.util.List;

public record EventCatalogEntry(
        String group,
        String name,
        ScriptTypePredicate scriptType,
        Class<?> eventType,
        Class<?> dispatchKeyType,
        boolean cancellable,
        boolean dispatchable,
        String snippet,
        List<EventCallSignature> callSignatures
) {
    public EventCatalogEntry {
        eventType = com.tkisor.nekojs.api.ScriptTypeViews.outputClass(eventType);
        callSignatures = List.copyOf(callSignatures);
    }

    public EventCatalogEntry(String group, String name, ScriptTypePredicate scriptType, Class<?> eventType,
                             Class<?> dispatchKeyType, boolean cancellable, boolean dispatchable, String snippet) {
        this(group, name, scriptType, eventType, dispatchKeyType, cancellable, dispatchable, snippet, List.of());
    }

    public EventCatalogEntry withCallSignatures(List<EventCallSignature> signatures) {
        return new EventCatalogEntry(group, name, scriptType, eventType, dispatchKeyType, cancellable, dispatchable,
                snippet, signatures);
    }

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

    /**
     * A custom event declared through {@code ScriptEvents}: the payload is a value the script
     * passes in, there is no event class ({@code eventType == null}, so the probe renders it as
     * {@code any}), and scripts can post it themselves.
     */
    public static EventCatalogEntry ofScriptEvent(String group, String name, ScriptTypePredicate scriptType) {
        return new EventCatalogEntry(group, name, scriptType, null, null, false, false,
                group + "." + name + "(payload => {\n  $0\n})");
    }

    /** Whether this is a custom event declared by a script (no event class; the probe renders an extra {@code post}). */
    public boolean scriptDefined() {
        return eventType == null;
    }
}
