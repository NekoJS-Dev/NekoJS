package com.tkisor.nekojs.api.event;

import com.tkisor.nekojs.api.data.BindingExposure;
import java.util.*;
import java.util.function.Function;

/** Module target, explicit stable event allowlist, and optional payload projections. */
public final class EventExposure {
    public record View(Class<?> type, Function<Object, ?> converter) {}
    private final BindingExposure target = new BindingExposure();
    private final Set<String> events = new LinkedHashSet<>();
    private final Map<String, View> views = new LinkedHashMap<>();
    private Set<com.tkisor.nekojs.api.ScriptType> scriptTypes = Set.copyOf(com.tkisor.nekojs.api.ScriptType.all());
    private boolean scopeSelected;
    private boolean frozen;

    public EventExposure stable() { checkMutable(); target.stable(); return this; }
    public EventExposure versioned() { checkMutable(); target.versioned(); return this; }
    /** Narrows publication to exact script types without widening the underlying source buses. */
    public EventExposure scriptTypes(com.tkisor.nekojs.api.ScriptType... types) {
        checkMutable();
        if (scopeSelected || types.length == 0) throw invalid("Event script types must be selected once and be nonempty");
        var selected = new LinkedHashSet<com.tkisor.nekojs.api.ScriptType>();
        for (var type : types) {
            if (type == null || !selected.add(type)) throw invalid("Event script types contain null or duplicates");
        }
        scriptTypes = Set.copyOf(selected);
        scopeSelected = true;
        return this;
    }
    public EventExposure events(String... names) {
        checkMutable();
        for (String name : names) {
            if (name == null || !events.add(name)) throw invalid("Event allowlist contains null or duplicate names");
        }
        return this;
    }
    public <E, V> EventExposure view(String name, Class<V> type, Function<E, V> converter) {
        checkMutable();
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(converter, "converter");
        @SuppressWarnings("unchecked") Function<Object, ?> erased = value -> converter.apply((E) value);
        if (views.putIfAbsent(name, new View(type, erased)) != null) throw invalid("Duplicate event view: " + name);
        return this;
    }
    public ModuleEventGroup freeze(String owner, EventGroup group) {
        return declaration().bind(owner, group);
    }

    Declaration declaration() {
        checkMutable();
        frozen = true;
        return new Declaration(target.freeze(), events, views, scriptTypes, events.isEmpty());
    }

    /** Owner-independent publication data, frozen when its definition callback returns. */
    record Declaration(BindingExposure.Category category, Set<String> events,
                       Map<String, View> views, Set<com.tkisor.nekojs.api.ScriptType> scriptTypes, boolean allEvents) {
        Declaration {
            events = Set.copyOf(events);
            views = Map.copyOf(views);
            scriptTypes = Set.copyOf(scriptTypes);
            if (category == BindingExposure.Category.STABLE && events.isEmpty()) {
                throw invalid("Stable events require an explicit allowlist");
            }
        }

        Declaration resolveEvents(EventGroup group) {
            return allEvents ? new Declaration(category, group.viewBuses().keySet(), views, scriptTypes, false) : this;
        }

        ModuleEventGroup bind(String owner, EventGroup group) {
            Set<String> names = allEvents ? group.viewBuses().keySet() : events;
            for (String name : names) if (!group.viewBuses().containsKey(name)) throw invalid("Unknown event: " + name);
            for (String name : views.keySet()) {
                if (!names.contains(name)) throw invalid("View is outside the event allowlist: " + name);
                if (com.tkisor.nekojs.api.ScriptType.all().stream()
                        .map(type -> group.getBusHolder(name).getBus(type)).filter(Objects::nonNull)
                        .anyMatch(EventBusJS::canCancel)) {
                    throw invalid("Cancellable event projections require a control mapping: " + name);
                }
            }
            if (category == BindingExposure.Category.STABLE) {
                for (String name : names) {
                    View view = views.get(name);
                    Class<?> payload = view == null ? com.tkisor.nekojs.api.ScriptType.all().stream()
                            .map(type -> group.getBusHolder(name).getBus(type)).filter(Objects::nonNull)
                            .findFirst().orElseThrow().eventType() : view.type();
                    if (payload != null) com.tkisor.nekojs.core.api.ModulePublicTypes.validateStable(payload, false);
                }
            }
            for (String name : names) {
                if (scriptTypes.stream().noneMatch(type -> group.getBusHolder(name).canApplyOn(type))) {
                    throw invalid("Event is unavailable in the selected script types: " + name);
                }
            }
            return new ModuleEventGroup(owner, category, group, names, views, scriptTypes);
        }
    }
    private void checkMutable() { if (frozen) throw invalid("Event exposure is frozen"); }
    private static IllegalArgumentException invalid(String message) { return new IllegalArgumentException("[NEKO-4057] " + message); }
}
