package com.tkisor.nekojs.bindings.static_access;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.data.Binding;
import com.tkisor.nekojs.api.event.EventBusForgeBridge;
import graal.graalvm.polyglot.Value;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.EventPriority;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 原生 Forge 事件桥（任意事件类 + 处理器直挂 {@link MinecraftForge#EVENT_BUS}），
 * 1.12.2 Cleanroom 版本。
 *
 * <p>1.12.2 的 Forge {@code EventBus} 只认方法上的 {@code @SubscribeEvent} 注解，没有
 * 现代 {@code addListener(Class, Consumer)}；这里复用 {@link EventBusForgeBridge} 的
 * 优先级分发槽（每个 {@link EventPriority} 一个槽类，按需懒注册），把脚本处理器注册为
 * 槽内动态条目，{@code receiveCancelled} 在槽内逐处理器过滤。
 *
 * <p>与 NeoForge 版的差异：泛型事件（{@code onGenericEvent}）在 1.12.2 没有运行时泛型
 * 分派（无 {@code GenericEvent} 基类），改用事件实例的 {@code getType()} 反射过滤——覆盖
 * {@code AttachCapabilitiesEvent} 这类按 {@code getType()} 区分持有者的泛型事件；事件
 * 不带 {@code getType()} 时不做过滤。
 *
 * <p>监听器返回 {@code true} 会翻译为 {@link Event#setCanceled(boolean)}（与全局取消
 * 约定一致）。实现 {@link Binding}：STARTUP reload 时 {@code close()} 注销上一轮全部
 * 原生监听器，避免 reload 后监听器累积。
 */
public class NativeEventsJS implements Binding {

    /** reload 清理用的注销句柄（bindNative 返回的 Runnable）。 */
    private static final List<Runnable> REMOVAL_TOKENS = new CopyOnWriteArrayList<>();

    public static void clear() {
        for (Runnable token : REMOVAL_TOKENS) {
            try {
                token.run();
            } catch (Exception e) {
                NekoJS.LOGGER.debug("NativeEvents unregister failed during clear: {}", e.toString());
            }
        }
        REMOVAL_TOKENS.clear();
    }

    public void onEvent(Object eventType, Object handler) {
        onEvent(EventPriority.NORMAL, false, eventType, handler);
    }

    public void onEvent(Object priorityObj, boolean receiveCancelled, Object eventType, Object handler) {
        registerNative(null, priorityObj, receiveCancelled, eventType, handler);
    }

    public void onEventTyped(Object priorityObj, boolean receiveCancelled, Object eventType, Object handler) {
        onEvent(priorityObj, receiveCancelled, eventType, handler);
    }

    public void onGenericEvent(Object genericClassType, Object eventType, Object handler) {
        onGenericEvent(genericClassType, EventPriority.NORMAL, false, eventType, handler);
    }

    public void onGenericEvent(Object genericClassType, Object priorityObj, boolean receiveCancelled, Object eventType, Object handler) {
        registerNative(resolveClass(genericClassType), priorityObj, receiveCancelled, eventType, handler);
    }

    public void onGenericEventTyped(Object genericClassType, Object priorityObj, boolean receiveCancelled, Object eventType, Object handler) {
        onGenericEvent(genericClassType, priorityObj, receiveCancelled, eventType, handler);
    }

    private void registerNative(Class<?> genericClass, Object priorityObj, boolean receiveCancelled, Object eventType, Object handler) {
        Class<? extends Event> eventClass = resolveEventClass(eventType);
        if (eventClass == null) return;

        EventPriority priority = resolvePriority(priorityObj);
        Value handlerValue = handler == null ? null : Value.asValue(handler);

        java.util.function.Consumer<Event> consumer = event -> {
            if (genericClass != null && !matchesGenericType(event, genericClass)) return;
            try {
                if (handlerValue == null) return;
                Value result = handlerValue.execute(event);
                if (result.isBoolean() && result.asBoolean() && event.isCancelable()) {
                    event.setCanceled(true);
                }
            } catch (Exception e) {
                NekoJS.LOGGER.debug("NativeEvent execution exception ({}): ", eventClass.getSimpleName(), e);
            }
        };

        REMOVAL_TOKENS.add(EventBusForgeBridge.bindNative(
                MinecraftForge.EVENT_BUS, eventClass, priority, receiveCancelled, consumer));
        NekoJS.LOGGER.debug("Native event registered successfully: {}", eventClass.getSimpleName());
    }

    /** 1.12.2 无 GenericEvent：按事件实例的 getType() 与请求的泛型类精确匹配过滤。 */
    private static boolean matchesGenericType(Event event, Class<?> genericClass) {
        Method getType = TYPE_GETTERS.computeIfAbsent(event.getClass(), cls -> {
            try {
                Method m = cls.getMethod("getType");
                if (Class.class.equals(m.getReturnType())) return m;
            } catch (NoSuchMethodException ignored) {
                // 事件不携带 getType()：无法过滤，等同不过滤
            }
            return null;
        });
        if (getType == null) return true;
        try {
            Object type = getType.invoke(event);
            return type != null && type.equals(genericClass);
        } catch (Exception e) {
            return false;
        }
    }

    private static final Map<Class<?>, Method> TYPE_GETTERS = new ConcurrentHashMap<>();

    /* ==================== 类与优先级解析（与 NeoForge 版同语义） ==================== */

    private static final Map<String, Class<?>> CLASS_CACHE = new ConcurrentHashMap<>();

    /**
     * 解析失败的类名负缓存（有上界）：CLASS_CACHE 只记成功，同一坏名字被反复解析时
     * 每次都会重跑 Class.forName 异常链；命中负缓存后直接返回 null。
     */
    private static final Set<String> CLASS_MISS_CACHE = ConcurrentHashMap.newKeySet();
    private static final int CLASS_MISS_CACHE_LIMIT = 256;

    Class<?> resolveClass(Object obj) {
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

    Class<?> resolveClassFromString(String className) {
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
    Class<? extends Event> resolveEventClass(Object obj) {
        Class<?> clazz = resolveClass(obj);
        if (clazz != null && Event.class.isAssignableFrom(clazz)) {
            return (Class<? extends Event>) clazz;
        }
        NekoJS.LOGGER.error("Target is not a valid Forge event: {}", obj);
        return null;
    }

    EventPriority resolvePriority(Object obj) {
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
