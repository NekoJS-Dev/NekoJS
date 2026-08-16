package com.tkisor.nekojs.bindings.static_access;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.api.ScriptType;
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
public class NativeEventsJS implements Binding {

    // CopyOnWriteArrayList: registration (JS/reload thread) and clear() (reload thread) can race.
    private static final List<Consumer<? extends Event>> REGISTERED_LISTENERS = new CopyOnWriteArrayList<>();

    public static void clear() {
        for (Consumer<? extends Event> listener : REGISTERED_LISTENERS) {
            NeoForge.EVENT_BUS.unregister(listener);
        }
        REGISTERED_LISTENERS.clear();
    }

    public void onEvent(Object eventType, Object handler) {
        onEvent(EventPriority.NORMAL, false, eventType, handler);
    }

    public void onEvent(Object priorityObj, boolean receiveCancelled, Object eventType, Object handler) {
        registerNative(priorityObj, receiveCancelled, eventType, handler);
    }

    public void onEventTyped(Object priorityObj, boolean receiveCancelled, Object eventType, Object handler) {
        onEvent(priorityObj, receiveCancelled, eventType, handler);
    }

    public void onGenericEvent(Object genericClassType, Object eventType, Object handler) {
        // 忽略 genericClassType，直接作为普通事件挂载
        onEvent(EventPriority.NORMAL, false, eventType, handler);
    }

    public void onGenericEvent(Object genericClassType, Object priorityObj, boolean receiveCancelled, Object eventType, Object handler) {
        registerNative(priorityObj, receiveCancelled, eventType, handler);
    }

    public void onGenericEventTyped(Object genericClassType, Object priorityObj, boolean receiveCancelled, Object eventType, Object handler) {
        onGenericEvent(genericClassType, priorityObj, receiveCancelled, eventType, handler);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void registerNative(Object priorityObj, boolean receiveCancelled, Object eventType, Object handler) {
        Class<? extends Event> eventClass = resolveEventClass(eventType);
        if (eventClass == null) return;

        EventPriority priority = resolvePriority(priorityObj);
        Value handlerValue = handler == null ? null : Value.asValue(handler);

        Consumer<Event> consumer = event -> {
            try {
                Value result = handlerValue.execute(event);
                if (result.isBoolean() && result.asBoolean()
                        && event instanceof ICancellableEvent cancellable) {
                    cancellable.setCanceled(true);
                }
            } catch (Exception e) {
                NekoJS.LOGGER.debug("NativeEvent execution exception (" + eventClass.getSimpleName() + "): ", e);
            }
        };

        NeoForge.EVENT_BUS.addListener(priority, receiveCancelled, (Class) eventClass, consumer);

        REGISTERED_LISTENERS.add(consumer);
        NekoJS.LOGGER.debug("Native event registered successfully: {}", eventClass.getSimpleName());
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
        NekoJS.LOGGER.debug("Failed to resolve class type: {}", obj);
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