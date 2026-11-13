package com.tkisor.nekojs.core.plugin;

import com.tkisor.nekojs.api.NekoJSPlugin;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.api.event.EventGroupRegistry;

import java.util.Map;

/**
 * The built-in {@code nekojs:events} extension point; a self-contained Point file (ADR-0001).
 * The merge policy is {@link MergePolicy#failFast}: a duplicate event group name is reported by
 * the registry at registration time.
 *
 * <p>The twin point {@code nekojs:client_events} ({@link ClientEventsPoint}) depends on this one.
 * This is the first built-in use of the V2 dependency semantics (ADR-0002).
 */
public final class EventsPoint {

    /** The extension point id; part of the observable contract. */
    public static final String ID = "nekojs:events";

    private EventsPoint() {
    }

    /** Explicit declaration form, collected the same way as overriding the matching NekoJSPlugin
     * hook: the plugin is collected by the {@code nekojs:events} extension point. */
    public interface Contributor extends NekoJSPlugin {

        /** Registers server event groups, such as {@code ServerEvents.*}/{@code PlayerEvents.*}. */
        default void registerEvents(EventGroupRegistry registry) {
        }
    }

    static Map<String, EventGroup> freezeEventGroups(EventGroupRegistry registry) {
        Map<String, EventGroup> view = registry.view();
        registry.sources().forEach(EventGroup::freeze);
        view.values().forEach(EventGroup::freeze);
        return new Snapshot(view, registry.moduleGroups(), registry.sources());
    }

    public static final class Snapshot extends java.util.AbstractMap<String, EventGroup> {
        private final Map<String, EventGroup> globals;
        private final java.util.List<com.tkisor.nekojs.api.event.ModuleEventGroup> modules;
        private final java.util.List<EventGroup> sources;
        Snapshot(Map<String, EventGroup> globals, java.util.List<com.tkisor.nekojs.api.event.ModuleEventGroup> modules,
                 java.util.List<EventGroup> sources) {
            this.globals = Map.copyOf(globals);
            this.modules = java.util.List.copyOf(modules);
            this.sources = java.util.List.copyOf(sources);
        }
        @Override public java.util.Set<Entry<String, EventGroup>> entrySet() { return globals.entrySet(); }
        public java.util.List<com.tkisor.nekojs.api.event.ModuleEventGroup> modules() { return modules; }
        public java.util.List<EventGroup> sources() { return sources; }
    }

    /**
     * Merged view of the event group products: a client process uses the client_events
     * product, which already includes the events product.
     */
    @SuppressWarnings("unchecked")
    public static Map<String, EventGroup> mergedEventGroups(Map<String, Object> products) {
        Map<String, EventGroup> clientGroups = (Map<String, EventGroup>) products.get(ClientEventsPoint.ID);
        if (clientGroups != null) {
            return clientGroups;
        }
        Map<String, EventGroup> groups = (Map<String, EventGroup>) products.get(ID);
        if (groups == null) {
            throw new IllegalStateException("Extension point '" + ID + "' produced no product for this bootstrap");
        }
        return groups;
    }

    /** Extension point definition, registered by the {@link NekoBuiltinPointsPlugin} manifest. */
    public static final NekoPluginExtensionPoint<NekoJSPlugin, EventGroupRegistry, Map<String, EventGroup>> POINT = point(plugin -> null);

    static NekoPluginExtensionPoint<NekoJSPlugin, EventGroupRegistry, Map<String, EventGroup>> point(
            java.util.function.Function<NekoJSPlugin, String> owners) {
        return NekoPluginExtensionPoint.<NekoJSPlugin, EventGroupRegistry, Map<String, EventGroup>>builder(ID, NekoJSPlugin.class)
                    .merge(MergePolicy.failFast())
                    .initializer(context -> new EventGroupRegistry.Impl())
                    .collector((plugin, registry) -> plugin.registerEvents(((EventGroupRegistry.Impl) registry).scopedTo(owners.apply(plugin))))
                    .finish(EventsPoint::freezeEventGroups)
                    .build();
    }
}
