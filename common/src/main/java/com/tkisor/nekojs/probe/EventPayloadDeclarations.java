package com.tkisor.nekojs.probe;

import com.tkisor.nekojs.api.catalog.EventCatalogEntry;
import com.tkisor.nekojs.api.catalog.RegistryBuilderSurfaceEntry;
import com.tkisor.nekojs.api.catalog.RegistryBuilderSurfaceEntry.EventPayload;

import java.util.ArrayList;
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
                List<RegistryBuilderSurfaceEntry.Member> members = new ArrayList<>(previous.members());
                for (RegistryBuilderSurfaceEntry.Member member : next.members()) {
                    if (!members.contains(member)) {
                        members.add(member);
                    }
                }
                return new EventPayload(previous.eventType(), previous.name(), members);
            });
        }
        return payloads;
    }

    public static List<String> builderNames(Map<Class<?>, EventPayload> payloads,
                                             List<RegistryBuilderSurfaceEntry> builders) {
        if (payloads.isEmpty()) {
            return List.of();
        }
        return builders.stream()
                .filter(builder -> builder.eventPayload() != null
                        && payloads.containsKey(builder.eventPayload().eventType()))
                .map(RegistryBuilderSurfaceEntry::builderName).distinct().sorted().toList();
    }
}
