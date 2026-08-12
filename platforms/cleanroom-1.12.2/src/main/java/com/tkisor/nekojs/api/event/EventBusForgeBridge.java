package com.tkisor.nekojs.api.event;

import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.EventBus;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * 用于将 Forge EventBus 与 EventBusJS 接驳在一起。
 * 1.12.2 Forge 版本 - 使用单个 @SubscribeEvent 分发器，内部分派到各 NekoJS 总线。
 */
public class EventBusForgeBridge {
    private static final Map<EventBus, ForgeEventDispatcher> DISPATCHERS = new ConcurrentHashMap<>();

    public static EventBusForgeBridge create(EventBus forgeBus) {
        return new EventBusForgeBridge(forgeBus);
    }

    private final EventBus forgeBus;
    private final ForgeEventDispatcher dispatcher;

    protected EventBusForgeBridge(EventBus forgeBus) {
        this.forgeBus = Objects.requireNonNull(forgeBus);
        this.dispatcher = DISPATCHERS.computeIfAbsent(forgeBus, bus -> {
            ForgeEventDispatcher d = new ForgeEventDispatcher();
            bus.register(d);
            return d;
        });
    }

    @SuppressWarnings("unchecked")
    public <E extends Event> EventBusForgeBridge bind(EventBusJS<E, ?> busJS, EventPriority priority, boolean receiveCancelled) {
        var bus = busJS.bus();
        boolean cancellable = bus instanceof CancellableEventBus<E>;
        dispatcher.register(bus.eventType(), event -> {
            if (cancellable) {
                if (busJS.post((E) event)) event.setCanceled(true);
            } else {
                busJS.post((E) event);
            }
        });
        return this;
    }

    public <E extends Event> EventBusForgeBridge bind(EventBusJS<E, ?> bus) {
        return bind(bus, EventPriority.NORMAL, false);
    }

    /**
     * 绑定一个带谓词过滤的 NekoJS 总线：仅当 Forge 事件通过 {@code filter} 时才投递给脚本。
     *
     * <p>用于 1.12.2 的 tick 类事件（{@code ServerTickEvent}/{@code PlayerTickEvent}/
     * {@code WorldTickEvent}）——它们用 {@code phase} 字段区分 Pre/Post，需要 filter 来实现
     * tickPre/tickPost 拆分，避免每个 phase 都触发两次脚本回调。filter 仅决定是否投递给脚本，
     * 不会取消被过滤掉的 Forge 事件本身。
     */
    public <E extends Event> EventBusForgeBridge bind(EventBusJS<E, ?> busJS, Predicate<E> filter) {
        return bind(busJS, filter, EventPriority.NORMAL, false);
    }

    @SuppressWarnings("unchecked")
    public <E extends Event> EventBusForgeBridge bind(
        EventBusJS<E, ?> busJS,
        Predicate<E> filter,
        EventPriority priority,
        boolean receiveCancelled
    ) {
        Objects.requireNonNull(filter, "filter");
        var bus = busJS.bus();
        boolean cancellable = bus instanceof CancellableEventBus<E>;
        dispatcher.register(bus.eventType(), event -> {
            E e = (E) event;
            if (!filter.test(e)) return;
            if (cancellable) {
                if (busJS.post(e)) event.setCanceled(true);
            } else {
                busJS.post(e);
            }
        });
        return this;
    }

    @SuppressWarnings("unchecked")
    public <E, E_FORGE extends Event> EventBusForgeBridge bindTransformed(
        EventBusJS<E, ?> busJS,
        Function<E_FORGE, E> transformer,
        Class<E_FORGE> eventType,
        EventPriority priority,
        boolean receiveCancelled
    ) {
        Objects.requireNonNull(busJS);
        Objects.requireNonNull(transformer);
        var bus = busJS.bus();
        boolean cancellable = bus instanceof CancellableEventBus<E>;
        dispatcher.register(eventType, event -> {
            E_FORGE forgeEvent = (E_FORGE) event;
            if (cancellable) {
                if (busJS.post(transformer.apply(forgeEvent))) event.setCanceled(true);
            } else {
                busJS.post(transformer.apply(forgeEvent));
            }
        });
        return this;
    }

    public <E, E_FORGE extends Event> EventBusForgeBridge bindTransformed(
        EventBusJS<E, ?> bus,
        Function<E_FORGE, E> transformer,
        Class<E_FORGE> eventType
    ) {
        return bindTransformed(bus, transformer, eventType, EventPriority.NORMAL, false);
    }

    /**
     * Single @SubscribeEvent listener registered on the Forge bus.
     * When any Forge event fires, this dispatches to the appropriate NekoJS handlers.
     */
    public static class ForgeEventDispatcher {
        /** 注册表：绑定的事件类型 -> 处理器列表。注册/重载时写入，事件派发时只读。 */
        private final Map<Class<? extends Event>, List<Consumer<Event>>> handlers = new ConcurrentHashMap<>();

        /**
         * 派发查找缓存：具体事件 class -> 该事件需要执行的处理器列表（已扁平化）。
         *
         * <p>首次遇到某个具体事件 class 时，对 handlers 做一次超类/接口扫描（isAssignableFrom）
         * 并缓存结果，之后同类型事件直接 O(1) 命中，tick 热路径不再每次全表扫描。
         * 容量有上限，防止事件类型无限增长（探测/动态事件等）导致缓存膨胀；
         * 注册新绑定时整体清空（见 {@link #register(Class, Consumer)}）。
         */
        private static final int MAX_CACHED_EVENT_CLASSES = 256;
        private final Map<Class<? extends Event>, List<Consumer<Event>>> lookupCache = new ConcurrentHashMap<>();

        void register(Class<? extends Event> eventClass, Consumer<Event> handler) {
            handlers.computeIfAbsent(eventClass, k -> new ArrayList<>()).add(handler);
            // 新增绑定可能命中任意已缓存的具体事件类，必须整体失效，下次派发时重新扫描。
            lookupCache.clear();
        }

        @SubscribeEvent
        public void onEvent(Event event) {
            for (Consumer<Event> h : resolve(event.getClass())) h.accept(event);
        }

        private List<Consumer<Event>> resolve(Class<? extends Event> eventClass) {
            List<Consumer<Event>> cached = lookupCache.get(eventClass);
            if (cached != null) {
                return cached;
            }
            List<Consumer<Event>> resolved = new ArrayList<>();
            for (Map.Entry<Class<? extends Event>, List<Consumer<Event>>> entry : handlers.entrySet()) {
                // 与旧的 isInstance(event) 全表扫描等价：事件是绑定类型的实例（含子类/接口实现）。
                if (entry.getKey().isAssignableFrom(eventClass)) {
                    resolved.addAll(entry.getValue());
                }
            }
            // 有界缓存：达到上限后不再缓存新事件类。已缓存类型仍 O(1)；
            // 新类型每次派发只扫一遍 handlers，而 handlers 数量远小于事件种类。
            if (lookupCache.size() < MAX_CACHED_EVENT_CLASSES) {
                List<Consumer<Event>> previous = lookupCache.putIfAbsent(eventClass, resolved);
                if (previous != null) {
                    return previous;
                }
            }
            return resolved;
        }
    }
}
