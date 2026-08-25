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
                // 降级必须可见（W4/A5）：Object.class 会让该事件的回调成员校验静默失效，
                // 旧行为连日志都没有，「预检为什么没报这个拼写」完全要靠推断
                com.tkisor.nekojs.core.error.Diagnostics.report(
                        "event-schema",
                        com.tkisor.nekojs.core.error.Diagnostics.Severity.WARN,
                        "事件组 " + group.name() + " 的事件 " + eventName
                                + " 无法解析事件类型，已降级为 Object（该事件的回调成员校验将被跳过）",
                        t);
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
