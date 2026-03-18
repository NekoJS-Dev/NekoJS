package com.tkisor.nekojs.bindings.static_access;

import com.tkisor.nekojs.NekoJS;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import org.graalvm.polyglot.Value;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class NativeEventsJS {

    private static final List<Consumer<? extends Event>> REGISTERED_LISTENERS = new ArrayList<>();

    public static void clear() {
        for (Consumer<? extends Event> listener : REGISTERED_LISTENERS) {
            NeoForge.EVENT_BUS.unregister(listener);
        }
        REGISTERED_LISTENERS.clear();
    }

    public void onEvent(Object eventType, Value handler) {
        onEvent(EventPriority.NORMAL, false, eventType, handler);
    }

    public void onEvent(Object priorityObj, boolean receiveCancelled, Object eventType, Value handler) {
        registerNative(priorityObj, receiveCancelled, eventType, handler);
    }

    public void onEventTyped(Object priorityObj, boolean receiveCancelled, Object eventType, Value handler) {
        onEvent(priorityObj, receiveCancelled, eventType, handler);
    }

    public void onGenericEvent(Object genericClassType, Object eventType, Value handler) {
        // 忽略 genericClassType，直接作为普通事件挂载
        onEvent(EventPriority.NORMAL, false, eventType, handler);
    }

    public void onGenericEvent(Object genericClassType, Object priorityObj, boolean receiveCancelled, Object eventType, Value handler) {
        registerNative(priorityObj, receiveCancelled, eventType, handler);
    }

    public void onGenericEventTyped(Object genericClassType, Object priorityObj, boolean receiveCancelled, Object eventType, Value handler) {
        onGenericEvent(genericClassType, priorityObj, receiveCancelled, eventType, handler);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void registerNative(Object priorityObj, boolean receiveCancelled, Object eventType, Value handler) {
        Class<? extends Event> eventClass = resolveEventClass(eventType);
        if (eventClass == null) return;

        EventPriority priority = resolvePriority(priorityObj);

        Consumer<Event> consumer = event -> {
            try {
                handler.executeVoid(event);
            } catch (Exception e) {
                NekoJS.LOGGER.error("[NekoJS] NativeEvent 执行异常 (" + eventClass.getSimpleName() + "): ", e);
            }
        };

        NeoForge.EVENT_BUS.addListener(priority, receiveCancelled, (Class) eventClass, consumer);

        REGISTERED_LISTENERS.add(consumer);
        NekoJS.LOGGER.debug("[NekoJS] 成功挂载原生事件: {}", eventClass.getSimpleName());
    }

    private static final Map<String, Class<?>> CLASS_CACHE = new ConcurrentHashMap<>();

    private Class<?> resolveClass(Object obj) {
        switch (obj) {
            case null -> {
                return null;
            }
            case Class<?> c -> {
                return c;
            }
            case String s -> {
                return resolveClassFromString(s);
            }
            case Value v -> {
                if (v.isString()) return resolveClassFromString(v.asString());
                try {
                    return v.as(Class.class);
                } catch (Exception ignored) {
                }
            }
            default -> {
            }
        }
        NekoJS.LOGGER.error("[NekoJS] 无法解析类类型: {}", obj);
        return null;
    }

    private Class<?> resolveClassFromString(String className) {
        if (CLASS_CACHE.containsKey(className)) {
            return CLASS_CACHE.get(className);
        }

        String currentName = className;
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

        while (true) {
            try {
                Class<?> clazz = Class.forName(currentName, false, classLoader);
                CLASS_CACHE.put(className, clazz);
                return clazz;
            } catch (ClassNotFoundException e) {
                int lastDotIndex = currentName.lastIndexOf('.');
                if (lastDotIndex == -1) {
                    NekoJS.LOGGER.error("[NekoJS] 找不到类，请检查类名拼写是否正确: {}", className);
                    return null;
                }
                currentName = currentName.substring(0, lastDotIndex) + '$' + currentName.substring(lastDotIndex + 1);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Class<? extends Event> resolveEventClass(Object obj) {
        Class<?> clazz = resolveClass(obj);
        if (clazz != null && Event.class.isAssignableFrom(clazz)) {
            return (Class<? extends Event>) clazz;
        }
        NekoJS.LOGGER.error("[NekoJS] 目标不是合法的 NeoForge Event: {}", obj);
        return null;
    }

    private EventPriority resolvePriority(Object obj) {
        if (obj instanceof EventPriority p) return p;
        if (obj instanceof String s) {
            try { return EventPriority.valueOf(s.toUpperCase()); }
            catch (Exception e) { NekoJS.LOGGER.warn("未知优先级: {}", s); }
        }
        if (obj instanceof Value v && v.isString()) return resolvePriority(v.asString());
        return EventPriority.NORMAL;
    }
}