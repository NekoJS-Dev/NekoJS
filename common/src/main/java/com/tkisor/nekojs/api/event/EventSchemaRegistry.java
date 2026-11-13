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
            } catch (Throwable t) {
                events.put(eventName, Object.class);
                // The fallback must be visible: with Object.class, member checks for this event's callbacks
                // stop silently, and nothing explains why preflight accepted a misspelled member.
                com.tkisor.nekojs.core.error.Diagnostics.report(
                        "event-schema",
                        com.tkisor.nekojs.core.error.Diagnostics.Severity.WARN,
                        "事件组 " + group.name() + " 的事件 " + eventName
                                + " 无法解析事件类型，已降级为 Object（该事件的回调成员校验将被跳过）",
                        t);
            }
        });
        // A group with the same name can be registered more than once (EventGroupRegistry.Impl.register
        // merges the groups), so the schema must merge with the existing one instead of overwriting it.
        // Overwriting silently disables event validation for every event of the earlier registration.
        SCHEMA.compute(group.name(), (name, existing) -> {
            Map<String, Class<?>> merged = existing == null ? new HashMap<>(events) : new HashMap<>(existing);
            merged.putAll(events);
            return Map.copyOf(merged);
        });
    }

    public static Class<?> resolve(String groupName, String eventName) {
        Map<String, Class<?>> events = SCHEMA.get(groupName);
        return events != null ? events.get(eventName) : null;
    }

    public static boolean isKnownGroup(String groupName) {
        return SCHEMA.containsKey(groupName);
    }
}
