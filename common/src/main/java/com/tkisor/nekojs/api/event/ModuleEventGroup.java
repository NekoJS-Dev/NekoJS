package com.tkisor.nekojs.api.event;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.data.Binding;
import com.tkisor.nekojs.api.data.BindingExposure;
import com.tkisor.nekojs.api.data.ModuleBinding;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Frozen module publication of existing event sources; owns no second bus or listener set. */
public record ModuleEventGroup(String owner, BindingExposure.Category category, EventGroup group,
                               Set<String> events, Map<String, EventExposure.View> views, Set<ScriptType> scriptTypes,
                               Map<String, Set<ScriptType>> eventScriptTypes) {
    public ModuleEventGroup(String owner, BindingExposure.Category category, EventGroup group,
                            Set<String> events, Map<String, EventExposure.View> views) {
        this(owner, category, group, events, views, Set.copyOf(ScriptType.all()));
    }
    public ModuleEventGroup(String owner, BindingExposure.Category category, EventGroup group,
                            Set<String> events, Map<String, EventExposure.View> views, Set<ScriptType> scriptTypes) {
        this(owner, category, group, events, views, scriptTypes, Map.of());
    }
    public ModuleEventGroup {
        events = Set.copyOf(events);
        views = Map.copyOf(views);
        scriptTypes = Set.copyOf(scriptTypes);
        Map<String, Set<ScriptType>> scopes = new LinkedHashMap<>();
        eventScriptTypes.forEach((name, types) -> scopes.put(name, Set.copyOf(types)));
        eventScriptTypes = Map.copyOf(scopes);
        new ModuleBinding(owner, category, ScriptType.STARTUP, Binding.of(group.name(), group));
    }
    public String moduleId() {
        return "nekojs/api/" + category.path() + ("nekojs".equals(owner) ? "" : "/" + owner);
    }
    public boolean available(ScriptType type) {
        return events.stream().anyMatch(name -> supports(name, type));
    }
    public boolean supports(String name, ScriptType type) {
        return events.contains(name) && eventScriptTypes.getOrDefault(name, scriptTypes).contains(type)
                && group.getBusHolder(name).canApplyOn(type);
    }

    /** Combines disjoint declarations while retaining each event's exact publication scope. */
    ModuleEventGroup merge(ModuleEventGroup other) {
        if (!owner.equals(other.owner) || category != other.category || !group.name().equals(other.group.name())) {
            throw new IllegalArgumentException("[NEKO-4057] Cannot merge unrelated module event groups");
        }
        Set<String> names = new LinkedHashSet<>(events);
        for (String name : other.events) {
            if (!names.add(name)) throw new IllegalArgumentException("[NEKO-4057] Duplicate module event: " + moduleId() + ":" + group.name() + "." + name);
        }
        EventGroup source = group;
        if (group != other.group) {
            source = EventGroup.of(group.name());
            // Only the published buses are needed here. Their identities and listener owners remain unchanged.
            copyPublishedBuses(source, this);
            copyPublishedBuses(source, other);
            source.freeze();
        }
        Map<String, EventExposure.View> projections = new LinkedHashMap<>(views);
        projections.putAll(other.views);
        Map<String, Set<ScriptType>> scopes = new LinkedHashMap<>();
        events.forEach(name -> scopes.put(name, eventScriptTypes.getOrDefault(name, scriptTypes)));
        other.events.forEach(name -> scopes.put(name, other.eventScriptTypes.getOrDefault(name, other.scriptTypes)));
        Set<ScriptType> types = new LinkedHashSet<>(scriptTypes);
        types.addAll(other.scriptTypes);
        return new ModuleEventGroup(owner, category, source, names, projections, types, scopes);
    }

    private static void copyPublishedBuses(EventGroup target, ModuleEventGroup publication) {
        for (String name : publication.group.viewBuses().keySet()) {
            if (!publication.events.contains(name)) continue;
            var holder = publication.group.getBusHolder(name);
            var bus = ScriptType.all().stream().map(holder::getBus).filter(java.util.Objects::nonNull).findFirst().orElseThrow();
            target.add(name, bus.scriptType(), bus);
        }
    }
}
