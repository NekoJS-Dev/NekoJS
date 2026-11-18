package com.tkisor.nekojs.api.event;

import org.jetbrains.annotations.ApiStatus;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Collects event sources and their explicit per-event module publications during plugin bootstrap.
 * Defining an {@link EventGroup} does not register it; registration binds publication declarations
 * to the contributing plugin's owner. Published targets retain their source buses and listener lifecycle.
 */
public interface EventGroupRegistry {
    /**
     * Registers a group's event sources and declarations under the contributing plugin owner.
     * Same-owner groups merge disjoint event names; duplicate events/publications fail. Different owners
     * may reuse group and event names. Only explicit publications are exposed, through event modules.
     */
    void register(EventGroup group);

    /** Returns the internal source index, keyed by owner and group; keys are not script globals. */
    Map<String, EventGroup> view();

    /** Returns the explicit stable/versioned event module publications. */
    default java.util.List<ModuleEventGroup> moduleGroups() { return java.util.List.of(); }
    /** Returns the original source groups retained for lifecycle cleanup and freezing. */
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
        @Override public java.util.List<ModuleEventGroup> moduleGroups() { return java.util.List.copyOf(modules.values()); }
        @Override public java.util.List<EventGroup> sources() { return java.util.List.copyOf(sources); }

        public void inherit(EventGroupRegistry registry) {
            registry.view().forEach((name, group) -> {
                EventGroup copy = EventGroup.of(group.name());
                copy.merge(group);
                groups.put(name, copy);
            });
            registry.moduleGroups().forEach(module -> modules.put(module.moduleId() + ":" + module.group().name(), module));
            registry.sources().forEach(this::addSource);
        }

        @Override
        public void register(EventGroup group) {
            Objects.requireNonNull(group, "group == null");
            // Validate publications before changing the owner-qualified source index or lifecycle ledger.
            Map<String, ModuleEventGroup> updated = new LinkedHashMap<>(modules);
            for (ModuleEventGroup publication : group.modulePublications(owner)) {
                String key = publication.moduleId() + ":" + group.name();
                updated.merge(key, publication, ModuleEventGroup::merge);
            }
            String sourceKey = owner == null ? group.name() : owner + ":" + group.name();
            EventGroup source = groups.computeIfAbsent(sourceKey, ignored -> EventGroup.of(group.name()));
            source.merge(group);
            modules.clear();
            modules.putAll(updated);
            addSource(group);
        }

        @Override
        public Map<String, EventGroup> view() {
            return Collections.unmodifiableMap(groups);
        }
    }
}
