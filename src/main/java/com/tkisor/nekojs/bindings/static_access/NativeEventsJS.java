//? if neoforge {
package com.tkisor.nekojs.bindings.static_access;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.api.annotation.Return;
import com.tkisor.nekojs.api.data.Binding;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.neoforge.common.NeoForge;
import graal.graalvm.polyglot.Value;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Bridge for raw NeoForge events: pass any event class and a handler, and the handler is attached
 * to {@code EVENT_BUS} directly.
 *
 * <p>Complements the declarative {@code ScriptEvents}, which registers named event groups at
 * STARTUP and lets server/client scripts listen by group: {@code NativeEvents} is for one-off or
 * ad-hoc listeners and introduces no named event group. Both are first-class APIs and coexist;
 * {@code NativeEvents} will not be folded into {@code ScriptEvents}.
 *
 * <p>A listener that returns {@code true} translates to {@code setCanceled(true)}, matching the
 * global cancel convention, and only takes effect for cancellable events. Implements
 * {@link Binding}: on a STARTUP reload, {@code close()} unregisters every native listener from
 * the previous round, so listeners do not pile up across reloads.
 */
@Doc("Raw NeoForge event bridge: listen to any NeoForge event class directly on the game event bus.")
@Doc("Complements the declarative ScriptEvents: use it for one-off or ad-hoc listeners without a named event group.")
@Doc("A listener returning true cancels the event (only when the event is cancellable).")
public class NativeEventsJS implements Binding {

    // CopyOnWriteArrayList: registration (JS/reload thread) and clear() (reload thread) can race.
    private static final List<Consumer<? extends Event>> REGISTERED_LISTENERS = new CopyOnWriteArrayList<>();

    /** Unregisters every native listener registered so far. {@link #close} calls this on a STARTUP reload. */
    @Doc("Unregisters every native listener registered so far.")
    public static void clear() {
        for (Consumer<? extends Event> listener : REGISTERED_LISTENERS) {
            NeoForge.EVENT_BUS.unregister(listener);
        }
        REGISTERED_LISTENERS.clear();
    }

    /** Listens to a raw event at NORMAL priority; a handler that returns {@code true} cancels a cancellable event. */
    @Doc("Listens to a NeoForge event with NORMAL priority.")
    @Param(name = "eventType", value = "event class or fully qualified class name, e.g. 'net.neoforged.neoforge.event.entity.living.LivingDeathEvent'")
    @Param(name = "handler", value = "callback receiving the event; returning true cancels a cancellable event")
    public void onEvent(Object eventType, Object handler) {
        onEvent(EventPriority.NORMAL, false, eventType, handler);
    }

    /**
     * Listens to a raw event at the given priority; {@code receiveCancelled} controls whether
     * already-cancelled events reach the handler.
     */
    @Doc("Listens to a NeoForge event with an explicit priority and cancelled-event policy.")
    @Param(name = "priorityObj", value = "EventPriority or its name like 'HIGH'; defaults to NORMAL on anything else")
    @Param(name = "receiveCancelled", value = "when true the handler also receives already-cancelled events")
    @Param(name = "eventType", value = "event class or fully qualified class name")
    @Param(name = "handler", value = "callback receiving the event; returning true cancels a cancellable event")
    public void onEvent(Object priorityObj, boolean receiveCancelled, Object eventType, Object handler) {
        registerNative(priorityObj, receiveCancelled, eventType, handler);
    }

    /** Explicitly typed alias of {@link #onEvent(Object, boolean, Object, Object)}. */
    @Doc("Alias of onEvent(priority, receiveCancelled, eventType, handler).")
    @Param(name = "priorityObj", value = "EventPriority or its name like 'HIGH'")
    @Param(name = "receiveCancelled", value = "when true the handler also receives already-cancelled events")
    @Param(name = "eventType", value = "event class or fully qualified class name")
    @Param(name = "handler", value = "callback receiving the event; returning true cancels a cancellable event")
    public void onEventTyped(Object priorityObj, boolean receiveCancelled, Object eventType, Object handler) {
        onEvent(priorityObj, receiveCancelled, eventType, handler);
    }

    /** Listens to a generic event. This is the KubeJS-compatible signature; {@code genericClassType} is ignored. */
    @Doc("Listens to a generic NeoForge event at NORMAL priority; the generic class argument is ignored.")
    @Param(name = "genericClassType", value = "ignored, kept for KubeJS compatibility")
    @Param(name = "eventType", value = "event class or fully qualified class name")
    @Param(name = "handler", value = "callback receiving the event; returning true cancels a cancellable event")
    public void onGenericEvent(Object genericClassType, Object eventType, Object handler) {
        // Ignore genericClassType and attach the event like any ordinary one
        onEvent(EventPriority.NORMAL, false, eventType, handler);
    }

    /**
     * Listens to a generic event with a priority and receiveCancelled. This is the
     * KubeJS-compatible signature; {@code genericClassType} is ignored.
     */
    @Doc("Listens to a generic NeoForge event with an explicit priority; the generic class argument is ignored.")
    @Param(name = "genericClassType", value = "ignored, kept for KubeJS compatibility")
    @Param(name = "priorityObj", value = "EventPriority or its name like 'HIGH'")
    @Param(name = "receiveCancelled", value = "when true the handler also receives already-cancelled events")
    @Param(name = "eventType", value = "event class or fully qualified class name")
    @Param(name = "handler", value = "callback receiving the event; returning true cancels a cancellable event")
    public void onGenericEvent(Object genericClassType, Object priorityObj, boolean receiveCancelled, Object eventType, Object handler) {
        registerNative(priorityObj, receiveCancelled, eventType, handler);
    }

    /** Explicitly typed alias of {@link #onGenericEvent(Object, Object, boolean, Object, Object)}. */
    @Doc("Alias of onGenericEvent(genericClassType, priority, receiveCancelled, eventType, handler).")
    @Param(name = "genericClassType", value = "ignored, kept for KubeJS compatibility")
    @Param(name = "priorityObj", value = "EventPriority or its name like 'HIGH'")
    @Param(name = "receiveCancelled", value = "when true the handler also receives already-cancelled events")
    @Param(name = "eventType", value = "event class or fully qualified class name")
    @Param(name = "handler", value = "callback receiving the event; returning true cancels a cancellable event")
    public void onGenericEventTyped(Object genericClassType, Object priorityObj, boolean receiveCancelled, Object eventType, Object handler) {
        onGenericEvent(genericClassType, priorityObj, receiveCancelled, eventType, handler);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void registerNative(Object priorityObj, boolean receiveCancelled, Object eventType, Object handler) {
        Class<? extends Event> eventClass = resolveEventClass(eventType);
        if (eventClass == null) return;

        EventPriority priority = resolvePriority(priorityObj);
        Value handlerValue = handler == null ? null : Value.asValue(handler);
        // Records the handler's owner (Context/ScriptType): an exception thrown during dispatch must reach
        // the error panel under that script type (the same channel as EventBusJS.recordListenerError),
        // not a single debug line. Otherwise a native event handler that throws is invisible and the
        // listener looks like it is running while failing silently on every call.
        graal.graalvm.polyglot.Context handlerContext = contextOf(handlerValue);
        ScriptType ownerType = handlerContext == null ? null
                : com.tkisor.nekojs.script.ScriptManager.getTypeFromContext(handlerContext);

        Consumer<Event> consumer = event -> {
            // Fallback for candidate generation isolation: a failed candidate's Context is already closed, but
            // native event registrations on the NeoForge EVENT_BUS cannot be undone together with that
            // candidate generation. Short-circuiting dispatch turns a leftover registration from a
            // discarded candidate into a no-op, matching the isContextDead gate in EventBusJS.
            if (com.tkisor.nekojs.script.ScriptManager.isContextDead(handlerContext)) {
                return;
            }
            try {
                Value result = handlerValue.execute(event);
                if (result.isBoolean() && result.asBoolean()
                        && event instanceof ICancellableEvent cancellable) {
                    cancellable.setCanceled(true);
                }
            } catch (Exception e) {
                com.tkisor.nekojs.script.ScriptManager.reportContextKilled(handlerContext, e);
                if (ownerType != null) {
                    com.tkisor.nekojs.api.event.ScriptErrorReporter.recordCallbackError(ownerType,
                            "native-event event=" + eventClass.getName()
                                    + " script=" + currentScriptIdOf(handlerContext), e);
                } else {
                    NekoJS.LOGGER.debug("NativeEvent execution exception (" + eventClass.getSimpleName() + "): ", e);
                }
            }
        };

        NeoForge.EVENT_BUS.addListener(priority, receiveCancelled, (Class) eventClass, consumer);

        REGISTERED_LISTENERS.add(consumer);
        NekoJS.LOGGER.debug("Native event registered successfully: {}", eventClass.getSimpleName());
    }

    private static graal.graalvm.polyglot.Context contextOf(Value value) {
        if (value == null) return null;
        try {
            return value.getContext();
        } catch (Exception e) {
            return null;
        }
    }

    private static String currentScriptIdOf(graal.graalvm.polyglot.Context context) {
        if (context == null) return "unknown";
        try {
            String scriptId = com.tkisor.nekojs.script.ScriptManager.getCurrentScriptId(context);
            return scriptId == null || scriptId.isBlank() ? "unknown" : scriptId;
        } catch (Exception e) {
            return "unknown";
        }
    }

    private static final Map<String, Class<?>> CLASS_CACHE = new ConcurrentHashMap<>();

    /**
     * Negative cache for class names that failed to resolve, with an upper bound. CLASS_CACHE only
     * records successes, so resolving the same bad name again reruns the Class.forName exception
     * chain every time. A hit here returns null directly, the same result as the failure path, and
     * the debug log is kept.
     */
    private static final Set<String> CLASS_MISS_CACHE = ConcurrentHashMap.newKeySet();
    private static final int CLASS_MISS_CACHE_LIMIT = 256;

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
        // Registration-time failure (the value cannot become a Class): the listener is never registered,
        // so this must be visible
        NekoJS.LOGGER.warn("Failed to resolve class type for native event registration: {}", obj);
        return null;
    }

    private Class<?> resolveClassFromString(String className) {
        if (CLASS_CACHE.containsKey(className)) {
            return CLASS_CACHE.get(className);
        }
        if (CLASS_MISS_CACHE.contains(className)) {
            NekoJS.LOGGER.debug("Class not found, please check the spelling of the class name: {}", className);
            return null;
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
                    NekoJS.LOGGER.debug("Class not found, please check the spelling of the class name: {}", className);
                    if (CLASS_MISS_CACHE.size() < CLASS_MISS_CACHE_LIMIT) {
                        CLASS_MISS_CACHE.add(className);
                    }
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
        NekoJS.LOGGER.error("Target is not a valid NeoForge event: {}", obj);
        return null;
    }

    private EventPriority resolvePriority(Object obj) {
        if (obj instanceof EventPriority p) return p;
        if (obj instanceof String s) {
            try { return EventPriority.valueOf(s.toUpperCase()); }
            catch (Exception e) { NekoJS.LOGGER.debug("Unknown priority value: {}", s); }
        }
        if (obj instanceof Value v && v.isString()) return resolvePriority(v.asString());
        return EventPriority.NORMAL;
    }

    // ---- Binding lifecycle: unregister the old native event listeners on a STARTUP reload ----

    @Override
    public String name() {
        return "NativeEvents";
    }

    @Override
    public Object value() {
        return this;
    }

    @Override
    public void close(ScriptType scriptType) {
        clear();
    }
}
//?}
