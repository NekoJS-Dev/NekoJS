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
 * 原生 NeoForge 事件桥（任意事件类 + 处理器直挂 EVENT_BUS）。
 *
 * <p>与声明式的 {@code ScriptEvents}（STARTUP 注册成命名事件组、server/client 脚本按组监听）
 * 互补：{@code NativeEvents} 面向一次性/就地监听，不引入命名事件组。两者均为一等 API
 * （2026-08-16 用户裁决：撤销 NativeEvents 弃用，D-2 由「收敛到 ScriptEvents」改为共存）。
 *
 * <p>监听器返回 {@code true} 会翻译为 {@code setCanceled(true)}（与全局取消约定一致；
 * 仅对可取消事件生效）。实现 {@link Binding}：STARTUP reload 时 {@code close()} 注销
 * 上一轮全部原生监听器，避免 reload 后监听器累积。
 */
@Doc("Raw NeoForge event bridge: listen to any NeoForge event class directly on the game event bus.")
@Doc("Complements the declarative ScriptEvents: use it for one-off or ad-hoc listeners without a named event group.")
@Doc("A listener returning true cancels the event (only when the event is cancellable).")
public class NativeEventsJS implements Binding {

    // CopyOnWriteArrayList: registration (JS/reload thread) and clear() (reload thread) can race.
    private static final List<Consumer<? extends Event>> REGISTERED_LISTENERS = new CopyOnWriteArrayList<>();

    /** 注销当前全部原生监听器（STARTUP reload 时由 {@link #close} 调用）。 */
    @Doc("Unregisters every native listener registered so far.")
    public static void clear() {
        for (Consumer<? extends Event> listener : REGISTERED_LISTENERS) {
            NeoForge.EVENT_BUS.unregister(listener);
        }
        REGISTERED_LISTENERS.clear();
    }

    /** 以 NORMAL 优先级监听原生事件（处理器返回 {@code true} 取消可取消事件）。 */
    @Doc("Listens to a NeoForge event with NORMAL priority.")
    @Param(name = "eventType", value = "event class or fully qualified class name, e.g. 'net.neoforged.neoforge.event.entity.living.LivingDeathEvent'")
    @Param(name = "handler", value = "callback receiving the event; returning true cancels a cancellable event")
    public void onEvent(Object eventType, Object handler) {
        onEvent(EventPriority.NORMAL, false, eventType, handler);
    }

    /** 以指定优先级监听原生事件（{@code receiveCancelled} 控制是否接收已取消事件）。 */
    @Doc("Listens to a NeoForge event with an explicit priority and cancelled-event policy.")
    @Param(name = "priorityObj", value = "EventPriority or its name like 'HIGH'; defaults to NORMAL on anything else")
    @Param(name = "receiveCancelled", value = "when true the handler also receives already-cancelled events")
    @Param(name = "eventType", value = "event class or fully qualified class name")
    @Param(name = "handler", value = "callback receiving the event; returning true cancels a cancellable event")
    public void onEvent(Object priorityObj, boolean receiveCancelled, Object eventType, Object handler) {
        registerNative(priorityObj, receiveCancelled, eventType, handler);
    }

    /** {@link #onEvent(Object, boolean, Object, Object)} 的显式类型别名。 */
    @Doc("Alias of onEvent(priority, receiveCancelled, eventType, handler).")
    @Param(name = "priorityObj", value = "EventPriority or its name like 'HIGH'")
    @Param(name = "receiveCancelled", value = "when true the handler also receives already-cancelled events")
    @Param(name = "eventType", value = "event class or fully qualified class name")
    @Param(name = "handler", value = "callback receiving the event; returning true cancels a cancellable event")
    public void onEventTyped(Object priorityObj, boolean receiveCancelled, Object eventType, Object handler) {
        onEvent(priorityObj, receiveCancelled, eventType, handler);
    }

    /** 泛型事件监听（KubeJS 兼容签名，genericClassType 被忽略）。 */
    @Doc("Listens to a generic NeoForge event at NORMAL priority; the generic class argument is ignored.")
    @Param(name = "genericClassType", value = "ignored, kept for KubeJS compatibility")
    @Param(name = "eventType", value = "event class or fully qualified class name")
    @Param(name = "handler", value = "callback receiving the event; returning true cancels a cancellable event")
    public void onGenericEvent(Object genericClassType, Object eventType, Object handler) {
        // 忽略 genericClassType，直接作为普通事件挂载
        onEvent(EventPriority.NORMAL, false, eventType, handler);
    }

    /** 泛型事件监听（KubeJS 兼容签名，genericClassType 被忽略），带优先级与 receiveCancelled。 */
    @Doc("Listens to a generic NeoForge event with an explicit priority; the generic class argument is ignored.")
    @Param(name = "genericClassType", value = "ignored, kept for KubeJS compatibility")
    @Param(name = "priorityObj", value = "EventPriority or its name like 'HIGH'")
    @Param(name = "receiveCancelled", value = "when true the handler also receives already-cancelled events")
    @Param(name = "eventType", value = "event class or fully qualified class name")
    @Param(name = "handler", value = "callback receiving the event; returning true cancels a cancellable event")
    public void onGenericEvent(Object genericClassType, Object priorityObj, boolean receiveCancelled, Object eventType, Object handler) {
        registerNative(priorityObj, receiveCancelled, eventType, handler);
    }

    /** {@link #onGenericEvent(Object, Object, boolean, Object, Object)} 的显式类型别名。 */
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
        // 记录 handler 的归属（Context/ScriptType）：分发时的异常必须按脚本类型进错误面板
        // （与 EventBusJS.recordListenerError 同一通道），而不是一行 debug——原生事件
        // handler 抛错后旧实现完全不可见，「监听器看起来在跑但每次都静默失败」（W4/A5）
        graal.graalvm.polyglot.Context handlerContext = contextOf(handlerValue);
        ScriptType ownerType = handlerContext == null ? null
                : com.tkisor.nekojs.script.ScriptManager.getTypeFromContext(handlerContext);

        Consumer<Event> consumer = event -> {
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
     * 解析失败的类名负缓存（有上界）：CLASS_CACHE 只记成功，同一坏名字被反复解析时
     * 每次都会重跑 Class.forName 异常链；命中负缓存后直接返回 null（与原失败路径一致，
     * debug 日志保留）。
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
        // 注册期失败（值转不成 Class）：监听器不会注册，必须可见（W4/A5）
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

    // ---- Binding 生命周期：STARTUP reload 时注销旧的原生事件监听器 ----

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