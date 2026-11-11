package com.tkisor.nekojs.api.event;

import org.jetbrains.annotations.ApiStatus;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public interface EventGroupRegistry {
    void register(EventGroup group);

    Map<String, EventGroup> view();

    default void register(EventGroup group, java.util.function.Consumer<EventExposure> configure) {
        throw new UnsupportedOperationException("[NEKO-4057] Module event registration is not supported by this registry");
    }

    default java.util.List<ModuleEventGroup> moduleGroups() { return java.util.List.of(); }
    default java.util.List<EventGroup> sources() { return java.util.List.copyOf(view().values()); }

    @ApiStatus.Internal
    final class Impl implements EventGroupRegistry {
        private final Map<String, EventGroup> groups;
        private final Map<String, ModuleEventGroup> modules;
        private final java.util.List<EventGroup> sources;
        private final String owner;

        public Impl() { this(new LinkedHashMap<>(), new LinkedHashMap<>(), new java.util.ArrayList<>(), null); }
        private Impl(Map<String, EventGroup> groups, Map<String, ModuleEventGroup> modules,
                     java.util.List<EventGroup> sources, String owner) {
            this.groups = groups;
            this.modules = modules;
            this.sources = sources;
            this.owner = owner;
        }
        public Impl scopedTo(String owner) { return new Impl(groups, modules, sources, owner); }
        private void addSource(EventGroup group) {
            if (sources.stream().noneMatch(source -> source == group)) sources.add(group);
        }
        @Override
        public void register(EventGroup group, java.util.function.Consumer<EventExposure> configure) {
            Objects.requireNonNull(group, "group");
            EventExposure exposure = new EventExposure();
            Objects.requireNonNull(configure, "configure").accept(exposure);
            ModuleEventGroup module = exposure.freeze(owner, group);
            String key = module.moduleId() + ":" + group.name();
            if (modules.putIfAbsent(key, module) != null) {
                throw new IllegalArgumentException("[NEKO-4057] Duplicate module event group: " + key);
            }
            addSource(group);
        }
        @Override public java.util.List<ModuleEventGroup> moduleGroups() { return java.util.List.copyOf(modules.values()); }
        @Override public java.util.List<EventGroup> sources() { return java.util.List.copyOf(sources); }

        public void inherit(EventGroupRegistry registry) {
            registry.view().forEach((name, group) -> {
                EventGroup copy = EventGroup.of(name);
                copy.merge(group);
                groups.put(name, copy);
            });
            registry.moduleGroups().forEach(module -> modules.put(module.moduleId() + ":" + module.group().name(), module));
            registry.sources().forEach(this::addSource);
        }

        @Override
        public void register(EventGroup group) {
            Objects.requireNonNull(group, "group == null");
            // Resolve every declaration and conflict before changing the legacy namespace or source ledger.
            Map<String, ModuleEventGroup> updated = new LinkedHashMap<>(modules);
            for (ModuleEventGroup publication : group.modulePublications(owner)) {
                String key = publication.moduleId() + ":" + group.name();
                updated.merge(key, publication, ModuleEventGroup::merge);
            }
            EventGroup legacy = groups.computeIfAbsent(group.name(), EventGroup::of);
            legacy.merge(group);
            modules.clear();
            modules.putAll(updated);
            addSource(group);
            EventSchemaRegistry.registerGroup(group);
        }

        @Override
        public Map<String, EventGroup> view() {
            return Collections.unmodifiableMap(groups);
        }
    }
}
