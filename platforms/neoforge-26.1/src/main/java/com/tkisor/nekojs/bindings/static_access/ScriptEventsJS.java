package com.tkisor.nekojs.bindings.static_access;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.ScriptEventDefinition;
import com.tkisor.nekojs.api.event.ScriptEventRegistrar;
import com.tkisor.nekojs.api.event.ScriptEventRegistry;
import com.tkisor.nekojs.script.ScriptType;
import graal.graalvm.polyglot.Value;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class ScriptEventsJS implements ScriptEventRegistrar {
    private static final Map<String, Class<?>> CLASS_CACHE = new ConcurrentHashMap<>();

    @Override
    public void register(ScriptType targetType, String groupName, String eventName, Object eventClass, String priority, boolean receiveCancelled) {
        registerNative(targetType, groupName, eventName, eventClass, resolvePriority(priority), receiveCancelled);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void registerNative(ScriptType targetType, String groupName, String eventName, Object eventClassValue, EventPriority priority, boolean receiveCancelled) {
        Class<? extends Event> eventClass = resolveEventClass(eventClassValue);
        validateName("group", groupName);
        validateName("name", eventName);
        ScriptEventRegistry.validateAvailable(targetType, groupName, eventName);

        EventBusJS<Event, Void> bus = (EventBusJS) EventBusJS.of((Class) eventClass);
        Consumer<Event> listener = event -> {
            if (bus.post(event) && event instanceof ICancellableEvent cancellable) {
                cancellable.setCanceled(true);
            }
        };

        NeoForge.EVENT_BUS.addListener(priority, receiveCancelled, (Class) eventClass, listener);
        ScriptEventRegistry.register(new ScriptEventDefinition(
                groupName,
                eventName,
                targetType,
                eventClass.getName(),
                "nekojs:startup/script_events",
                bus,
                () -> NeoForge.EVENT_BUS.unregister(listener)
        ));
        NekoJS.LOGGER.debug("Script event registered: {}.{} -> {}", groupName, eventName, eventClass.getName());
    }

    private static void validateName(String field, String value) {
        if (value == null || !value.matches("[A-Za-z_$][A-Za-z0-9_$]*")) {
            throw new IllegalArgumentException("ScriptEvents " + field + " must be a valid JS identifier: " + value);
        }
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends Event> resolveEventClass(Object value) {
        Class<?> clazz = resolveClass(value);
        if (clazz != null && Event.class.isAssignableFrom(clazz)) {
            return (Class<? extends Event>) clazz;
        }
        throw new IllegalArgumentException("Target is not a valid NeoForge event: " + value);
    }

    private static Class<?> resolveClass(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Class<?> clazz) {
            return clazz;
        }
        if (value instanceof String className) {
            return resolveClassFromString(className);
        }
        if (value instanceof Value polyglotValue) {
            if (polyglotValue.isString()) {
                return resolveClassFromString(polyglotValue.asString());
            }
            if (polyglotValue.isHostObject() && polyglotValue.asHostObject() instanceof Class<?> clazz) {
                return clazz;
            }
            try {
                return polyglotValue.as(Class.class);
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    private static Class<?> resolveClassFromString(String className) {
        Class<?> cached = CLASS_CACHE.get(className);
        if (cached != null) {
            return cached;
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
                    throw new IllegalArgumentException("Class not found: " + className, e);
                }
                currentName = currentName.substring(0, lastDotIndex) + '$' + currentName.substring(lastDotIndex + 1);
            }
        }
    }

    private static EventPriority resolvePriority(String priority) {
        if (priority == null || priority.isBlank()) {
            return EventPriority.NORMAL;
        }
        return EventPriority.valueOf(priority.toUpperCase());
    }
}
