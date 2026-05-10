package com.tkisor.nekojs.api;

import com.tkisor.nekojs.api.catalog.NekoScriptCatalog;
import com.tkisor.nekojs.api.data.Binding;
import com.tkisor.nekojs.api.data.NekoBindings;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.api.event.NekoEventGroups;
import com.tkisor.nekojs.script.ScriptType;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

@Deprecated(forRemoval = false)
public final class NekoContextSnapshot {
    private final ScriptType scriptType;
    private final Map<String, Binding> bindings;
    private final Map<String, EventGroup> eventGroups;
    private final Set<String> recipeNamespaces;
    private final Set<Class<?>> typeAdapterTargets;

    private NekoContextSnapshot(
            ScriptType scriptType,
            Map<String, Binding> bindings,
            Map<String, EventGroup> eventGroups,
            Set<String> recipeNamespaces,
            Set<Class<?>> typeAdapterTargets
    ) {
        this.scriptType = scriptType;
        this.bindings = Collections.unmodifiableMap(bindings);
        this.eventGroups = Collections.unmodifiableMap(eventGroups);
        this.recipeNamespaces = Collections.unmodifiableSet(recipeNamespaces);
        this.typeAdapterTargets = Collections.unmodifiableSet(typeAdapterTargets);
    }

    public static NekoContextSnapshot of(ScriptType scriptType) {
        var catalog = NekoScriptCatalog.snapshot(scriptType);

        Map<String, Binding> bindings = NekoBindings.getFor(scriptType);
        Map<String, EventGroup> filteredGroups = new LinkedHashMap<>();
        for (EventGroup group : NekoEventGroups.all().values()) {
            boolean hasApplicableBus = group.viewBuses().values().stream().anyMatch(holder -> holder.canApplyOn(scriptType));
            if (hasApplicableBus) {
                filteredGroups.put(group.name(), group);
            }
        }

        Set<String> recipeNamespaces = new LinkedHashSet<>();
        for (var entry : catalog.recipeNamespaces()) {
            recipeNamespaces.add(entry.namespace());
        }

        Set<Class<?>> adapterTargets = new LinkedHashSet<>();
        for (var entry : catalog.adapters()) {
            adapterTargets.add(entry.targetType());
        }

        return new NekoContextSnapshot(scriptType, bindings, filteredGroups, recipeNamespaces, adapterTargets);
    }

    public ScriptType scriptType() {
        return scriptType;
    }

    public Map<String, Binding> bindings() {
        return bindings;
    }

    public Map<String, EventGroup> eventGroups() {
        return eventGroups;
    }

    public Set<String> recipeNamespaces() {
        return recipeNamespaces;
    }

    public Set<Class<?>> typeAdapterTargets() {
        return typeAdapterTargets;
    }

    public static @Nullable Class<?> getHandlerClassForNamespace(String namespace) {
        return NekoScriptCatalog.snapshot().recipeNamespaces().stream()
                .filter(entry -> entry.namespace().equals(namespace))
                .map(entry -> entry.handlerClass())
                .findFirst()
                .orElse(null);
    }
}
