package com.tkisor.nekojs.core.plugin;

import com.tkisor.nekojs.api.NekoJSPlugin;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.api.event.EventGroupRegistry;

import java.util.Map;

/**
 * The built-in {@code nekojs:client_events} extension point; a self-contained Point file
 * (ADR-0001) and <b>the first built-in use of the V2 dependency semantics (ADR-0002)</b>:
 *
 * <ul>
 *   <li>Ordering dependency: {@code dependsOn(EventsPoint.POINT)} - it only needs events to
 *       finish first;</li>
 *   <li>Data dependency: the initializer reads the events product with
 *       {@code context.result(EventsPoint.POINT)} and uses it as the accumulator's initial state
 *       (client event groups merged with server event groups). No declaration is needed, and the
 *       topological order keeps it safe;</li>
 *   <li>Collected on the client only; a dedicated server process skips the whole point and the
 *       event group product falls back to the events product (see
 *       {@link EventsPoint#mergedEventGroups}).</li>
 * </ul>
 */
public final class ClientEventsPoint {

    /** The extension point id; part of the observable contract. */
    public static final String ID = "nekojs:client_events";

    private ClientEventsPoint() {
    }

    /** Explicit declaration form, collected the same way as overriding the matching NekoJSPlugin
     * hook: the plugin is collected by the {@code nekojs:client_events} extension point. */
    public interface Contributor extends NekoJSPlugin {

        /** Registers client event groups, visible only in a client runtime, such as
         * {@code ClientEvents.*}. */
        default void registerClientEvents(EventGroupRegistry registry) {
        }
    }

    /** The extension point definition, registered by the {@link NekoBuiltinPointsPlugin} manifest. */
    public static final NekoPluginExtensionPoint<NekoJSPlugin, EventGroupRegistry, Map<String, EventGroup>> POINT = point(plugin -> null);

    static NekoPluginExtensionPoint<NekoJSPlugin, EventGroupRegistry, Map<String, EventGroup>> point(
            java.util.function.Function<NekoJSPlugin, String> owners) {
        return NekoPluginExtensionPoint.<NekoJSPlugin, EventGroupRegistry, Map<String, EventGroup>>builder(ID, NekoJSPlugin.class)
                    .merge(MergePolicy.failFast())
                    .clientOnly()
                    .dependsOn(EventsPoint.POINT)
                    .initializer(context -> {
                        EventGroupRegistry.Impl merged = new EventGroupRegistry.Impl();
                        Map<String, EventGroup> earlier = context.result(EventsPoint.POINT);
                        if (earlier != null) {
                            if (earlier instanceof EventsPoint.Snapshot snapshot) {
                                merged.inherit(new EventGroupRegistry() {
                                    public void register(EventGroup group) { throw new UnsupportedOperationException(); }
                                    public Map<String, EventGroup> view() { return earlier; }
                                    public java.util.List<com.tkisor.nekojs.api.event.ModuleEventGroup> moduleGroups() { return snapshot.modules(); }
                                    public java.util.List<EventGroup> sources() { return snapshot.sources(); }
                                });
                            } else {
                                earlier.values().forEach(merged::register);
                            }
                        }
                        return merged;
                    })
                    .collector((plugin, registry) -> plugin.registerClientEvents(((EventGroupRegistry.Impl) registry).scopedTo(owners.apply(plugin))))
                    .finish(EventsPoint::freezeEventGroups)
                    .build();
    }
}
