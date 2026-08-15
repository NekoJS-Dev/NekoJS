package com.tkisor.nekojs.bindings.static_access;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.ScriptEventDefinition;
import com.tkisor.nekojs.api.event.ScriptEventRegistrar;
import com.tkisor.nekojs.api.event.ScriptEventRegistry;
import com.tkisor.nekojs.api.plugin.IPluginRuntime;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.script.ScriptContextRegistry;
import graal.graalvm.polyglot.Value;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class ScriptEventsJS implements ScriptEventRegistrar {
    private static final Map<String, Class<?>> CLASS_CACHE = new ConcurrentHashMap<>();

    /**
     * 解析失败的类名负缓存（有上界）：CLASS_CACHE 只记成功，同一坏名字被反复解析时
     * 每次都会重跑 Class.forName 异常链；命中负缓存后直接抛同类型的
     * IllegalArgumentException（仅丢失最底层 cause，消息不变）。
     */
    private static final Set<String> CLASS_MISS_CACHE = ConcurrentHashMap.newKeySet();
    private static final int CLASS_MISS_CACHE_LIMIT = 256;

    private IPluginRuntime pluginRuntime;

    public void bindRuntime(IPluginRuntime pluginRuntime) {
        if (pluginRuntime == null) {
            throw new IllegalArgumentException("pluginRuntime == null");
        }
        this.pluginRuntime = pluginRuntime;
    }

    private IPluginRuntime pluginRuntime() {
        if (pluginRuntime == null) {
            throw new IllegalStateException("ScriptEventsJS runtime has not been bound yet");
        }
        return pluginRuntime;
    }

    @Override
    public void register(ScriptType targetType, String groupName, String eventName, Object eventClass, String priority, boolean receiveCancelled) {
        registerNative(targetType, groupName, eventName, eventClass, resolvePriority(priority), receiveCancelled);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void registerNative(ScriptType targetType, String groupName, String eventName, Object eventClassValue, EventPriority priority, boolean receiveCancelled) {
        Class<? extends Event> eventClass = resolveEventClass(eventClassValue);
        validateName("group", groupName);
        validateName("name", eventName);
        IPluginRuntime runtime = pluginRuntime();
        ScriptEventRegistry.validateAvailable(runtime, targetType, groupName, eventName);

        EventBusJS<Event, Void> bus = (EventBusJS) EventBusJS.of((Class) eventClass);
        bus.metadata(groupName, eventName);
        Consumer<Event> listener = event -> {
            if (bus.post(event) && event instanceof ICancellableEvent cancellable) {
                cancellable.setCanceled(true);
            }
        };

        NeoForge.EVENT_BUS.addListener(priority, receiveCancelled, (Class) eventClass, listener);
        ScriptEventRegistry.register(runtime, new ScriptEventDefinition(
                groupName,
                eventName,
                targetType,
                eventClass.getName(),
                resolveSourceScriptId(eventClassValue),
                bus,
                () -> NeoForge.EVENT_BUS.unregister(listener)
        ));
        NekoJS.LOGGER.debug("Script event registered: {}.{} -> {}", groupName, eventName, eventClass.getName());
    }

    /**
     * Resolve the source script id for a ScriptEvents registration.
     *
     * <p>BUG-B3: the old implementation always registered with the constant
     * {@code "nekojs:startup/script_events"}, so per-script STARTUP reload
     * ({@code clearDefinitions(STARTUP, scriptId)}) never matched and the re-run
     * threw "Script event already registered".
     */
    static String resolveSourceScriptId(Object eventClassValue) {
        if (eventClassValue instanceof Value polyglotValue) {
            String scriptId = ScriptContextRegistry.currentScriptIdOf(polyglotValue.getContext());
            if (scriptId != null && !scriptId.isBlank()) {
                return scriptId;
            }
        }
        return "nekojs:startup/script_events";
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
        if (CLASS_MISS_CACHE.contains(className)) {
            throw new IllegalArgumentException("Class not found: " + className);
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
                    if (CLASS_MISS_CACHE.size() < CLASS_MISS_CACHE_LIMIT) {
                        CLASS_MISS_CACHE.add(className);
                    }
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
