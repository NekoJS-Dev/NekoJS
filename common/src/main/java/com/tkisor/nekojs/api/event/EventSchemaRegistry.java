package com.tkisor.nekojs.api.event;

import com.tkisor.nekojs.api.ScriptType;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class EventSchemaRegistry {
    private static final Map<String, Map<String, Class<?>>> SCHEMA = new ConcurrentHashMap<>();

    private EventSchemaRegistry() {}

    public static void registerGroup(EventGroup group) {
        Map<String, Class<?>> events = new HashMap<>();
        group.viewBuses().forEach((eventName, busHolder) -> {
            try {
                EventBusJS<?, ?> bus = busHolder.getBus(ScriptType.STARTUP);
                if (bus != null) events.put(eventName, bus.eventType());
            } catch (Throwable ignored) {
                events.put(eventName, Object.class);
            }
        });
        SCHEMA.put(group.name(), Map.copyOf(events));
    }

    public static Class<?> resolve(String groupName, String eventName) {
        Map<String, Class<?>> events = SCHEMA.get(groupName);
        return events != null ? events.get(eventName) : null;
    }

    public static boolean isKnownGroup(String groupName) {
        return SCHEMA.containsKey(groupName);
    }
}
