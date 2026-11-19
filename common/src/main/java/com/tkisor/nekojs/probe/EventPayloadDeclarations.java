package com.tkisor.nekojs.probe;

import com.google.gson.JsonPrimitive;
import com.tkisor.nekojs.api.catalog.EventCatalogEntry;
import com.tkisor.nekojs.api.catalog.RegistryBuilderSurfaceEntry;
import com.tkisor.nekojs.api.catalog.RegistryBuilderSurfaceEntry.EventPayload;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Resolves explicit type_docs payload associations without scanning the event host class. */
public final class EventPayloadDeclarations {
    private EventPayloadDeclarations() {}

    public static Map<Class<?>, EventPayload> resolve(List<EventCatalogEntry> events,
                                                       List<RegistryBuilderSurfaceEntry> builders) {
        if (builders == null || builders.isEmpty()) {
            return Collections.emptyMap();
        }
        Set<Class<?>> eventTypes = new LinkedHashSet<>();
        for (EventCatalogEntry event : events) {
            if (!event.scriptDefined()) {
                eventTypes.add(event.eventType());
            }
        }
        Map<Class<?>, EventPayload> payloads = new LinkedHashMap<>();
        for (RegistryBuilderSurfaceEntry builder : builders) {
            EventPayload payload = builder.eventPayload();
            if (payload == null || !eventTypes.contains(payload.eventType())) {
                continue;
            }
            payloads.merge(payload.eventType(), payload, (previous, next) -> {
                if (previous.equals(next)) {
                    return previous;
                }
                var members = new LinkedHashSet<>(previous.members());
                members.addAll(next.members());
                var variants = new LinkedHashSet<>(previous.variants());
                variants.addAll(next.variants());
                return new EventPayload(previous.eventType(), previous.name(), List.copyOf(members),
                        previous.typescriptBuilderNamespace(), List.copyOf(variants));
            });
        }
        return payloads;
    }

    public static String quoteSelector(String selector) {
        return new JsonPrimitive(selector).toString();
    }

    public static List<String> builderNames(Map<Class<?>, EventPayload> payloads,
                                             List<RegistryBuilderSurfaceEntry> builders) {
        return associatedBuilders(payloads, builders).stream()
                .map(RegistryBuilderSurfaceEntry::builderName).distinct().sorted().toList();
    }

    public static List<RegistryBuilderSurfaceEntry> associatedBuilders(Map<Class<?>, EventPayload> payloads,
                                                                       List<RegistryBuilderSurfaceEntry> builders) {
        if (payloads.isEmpty()) {
            return List.of();
        }
        return builders.stream()
                .filter(RegistryBuilderSurfaceEntry::hasBuilder)
                .filter(builder -> builder.eventPayload() != null
                        && payloads.containsKey(builder.eventPayload().eventType()))
                .toList();
    }
}
