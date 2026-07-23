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
        private final Map<Class<? extends Event>, List<Consumer<Event>>> handlers = new ConcurrentHashMap<>();

        void register(Class<? extends Event> eventClass, Consumer<Event> handler) {
            handlers.computeIfAbsent(eventClass, k -> new ArrayList<>()).add(handler);
        }

        @SubscribeEvent
        public void onEvent(Event event) {
            for (Map.Entry<Class<? extends Event>, List<Consumer<Event>>> entry : handlers.entrySet()) {
                if (entry.getKey().isInstance(event)) {
                    for (Consumer<Event> h : entry.getValue()) h.accept(event);
                }
            }
        }
    }
}
