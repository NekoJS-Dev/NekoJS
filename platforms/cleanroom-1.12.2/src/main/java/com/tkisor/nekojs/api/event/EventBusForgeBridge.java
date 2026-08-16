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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * 用于将 Forge EventBus 与 EventBusJS 接驳在一起。
 * 1.12.2 Cleanroom 版本 - 每个用到的 EventPriority 一个 @SubscribeEvent 分发槽，
 * 槽内按各监听器的 receiveCancelled 过滤后分派到 NekoJS 总线
 * （实现细节见 {@link ForgeEventDispatcher}）。
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
        this.dispatcher = DISPATCHERS.computeIfAbsent(forgeBus, ForgeEventDispatcher::new);
    }

    @SuppressWarnings("unchecked")
    public <E extends Event> EventBusForgeBridge bind(EventBusJS<E, ?> busJS, EventPriority priority, boolean receiveCancelled) {
        var bus = busJS.bus();
        boolean cancellable = bus instanceof CancellableEventBus<E>;
        dispatcher.register(bus.eventType(), priority, receiveCancelled, event -> {
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
        dispatcher.register(bus.eventType(), priority, receiveCancelled, event -> {
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
        dispatcher.register(eventType, priority, receiveCancelled, event -> {
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
     * 动态注册一个原生事件处理器（{@code NativeEvents} 等脚本侧 API 使用），返回注销句柄。
     *
     * <p>与静态 {@link #bind(EventBusJS)} 的区别：静态绑定随 bootstrap 一次性注册、永不注销；
     * 动态注册的处理器归脚本所有，句柄供 STARTUP reload 清理（重复调用幂等）。
     * 取消约定与静态绑定一致：处理器自行在 {@link Consumer} 内决定是否 {@code setCanceled(true)}。
     */
    public static Runnable bindNative(
            EventBus forgeBus,
            Class<? extends Event> eventClass,
            EventPriority priority,
            boolean receiveCancelled,
            Consumer<Event> handler) {
        ForgeEventDispatcher dispatcher = DISPATCHERS.computeIfAbsent(forgeBus, ForgeEventDispatcher::new);
        dispatcher.register(eventClass, priority, receiveCancelled, handler);
        AtomicBoolean removed = new AtomicBoolean();
        return () -> {
            if (removed.compareAndSet(false, true)) {
                dispatcher.remove(eventClass, priority, handler);
            }
        };
    }

    /**
     * NekoJS 处理器注册中心 + 按优先级注册到 Forge 总线的分发槽。
     *
     * <p>1.12.2 Forge 只认方法上的 @SubscribeEvent 注解，而注解的 priority 属性是
     * 编译期常量，因此每个 {@link EventPriority} 需要一个独立的槽类
     * （{@link HighestSlot} 等 {@link PrioritySlot} 子类）。槽按需懒注册：首次有
     * 该优先级的绑定时才 {@code forgeBus.register(slot)}，未用到的优先级零开销。
     *
     * <p>receiveCancelled 的实现：所有槽都以 {@code receiveCanceled = true} 注册
     * （否则 receiveCancelled = true 的监听器永远收不到已取消事件），再在槽内按
     * 每个监听器自己的标志过滤——{@code receiveCancelled == false} 的处理器在事件
     * 已取消时跳过。这与 Forge 对 {@code receiveCanceled = false} 监听器的语义一致：
     * 事件在轮到该监听器之前被取消（无论被谁取消，包括同一槽内更早的 NekoJS
     * 处理器）时收不到该事件。限制：注册粒度是"优先级槽"而不是单个监听器，
     * 已取消事件仍会进入本优先级的槽方法（随后被过滤为空操作）。
     */
    public static class ForgeEventDispatcher {
        /**
         * 注册表：绑定的事件类型 -> 处理器列表。注册/重载时写入，事件派发时只读。
         * 列表使用 {@link CopyOnWriteArrayList}：脚本热重载可能在与派发线程不同的
         * 线程上 register()，而 {@link #resolve(Class)} 扁平化时会并发读这些列表，
         * 写时复制避免遍历期间的 ConcurrentModificationException。
         */
        private final Map<Class<? extends Event>, List<PrioritizedHandler>> handlers = new ConcurrentHashMap<>();

        /** 已注册到 Forge 总线的优先级分发槽（懒注册，见 {@link #ensureSlotRegistered}）。 */
        private final Map<EventPriority, PrioritySlot> slots = new ConcurrentHashMap<>();

        private final EventBus forgeBus;

        /**
         * 派发查找缓存：具体事件 class -> 该事件需要执行的处理器列表（已扁平化）。
         *
         * <p>首次遇到某个具体事件 class 时，对 handlers 做一次超类/接口扫描（isAssignableFrom）
         * 并缓存结果，之后同类型事件直接 O(1) 命中，tick 热路径不再每次全表扫描。
         * 容量有上限，防止事件类型无限增长（探测/动态事件等）导致缓存膨胀；
         * 注册新绑定时整体清空（见 {@link #register(Class, EventPriority, boolean, Consumer)}）。
         */
        private static final int MAX_CACHED_EVENT_CLASSES = 256;
        private final Map<Class<? extends Event>, List<PrioritizedHandler>> lookupCache = new ConcurrentHashMap<>();

        ForgeEventDispatcher(EventBus forgeBus) {
            this.forgeBus = forgeBus;
        }

        void register(Class<? extends Event> eventClass, EventPriority priority, boolean receiveCancelled, Consumer<Event> handler) {
            handlers.computeIfAbsent(eventClass, k -> new CopyOnWriteArrayList<>())
                    .add(new PrioritizedHandler(priority, receiveCancelled, handler));
            // 新增绑定可能命中任意已缓存的具体事件类，必须整体失效，下次派发时重新扫描。
            lookupCache.clear();
            ensureSlotRegistered(priority);
        }

        /** 首次用到某优先级时，把对应的 @SubscribeEvent 槽注册到 Forge 总线。 */
        private void ensureSlotRegistered(EventPriority priority) {
            slots.computeIfAbsent(priority, p -> {
                PrioritySlot slot = switch (p) {
                    case HIGHEST -> new HighestSlot(this);
                    case HIGH -> new HighSlot(this);
                    case NORMAL -> new NormalSlot(this);
                    case LOW -> new LowSlot(this);
                    case LOWEST -> new LowestSlot(this);
                };
                forgeBus.register(slot);
                return slot;
            });
        }

        /** 注销一个动态注册的处理器（按 consumer 实例身份匹配；回收查找缓存）。 */
        void remove(Class<? extends Event> eventClass, EventPriority priority, Consumer<Event> handler) {
            List<PrioritizedHandler> list = handlers.get(eventClass);
            if (list == null) return;
            list.removeIf(h -> h.priority() == priority && h.handler() == handler);
            lookupCache.clear();
        }

        void dispatch(Event event, EventPriority priority) {
            for (PrioritizedHandler h : resolve(event.getClass())) {
                if (h.priority() != priority) continue;
                // 每次迭代重新读取取消状态：同一槽内更早的处理器取消事件后，
                // 后续 receiveCancelled=false 的处理器要像 Forge 一样被跳过。
                if (!h.receiveCancelled() && event.isCanceled()) continue;
                h.handler().accept(event);
            }
        }

        private List<PrioritizedHandler> resolve(Class<? extends Event> eventClass) {
            List<PrioritizedHandler> cached = lookupCache.get(eventClass);
            if (cached != null) {
                return cached;
            }
            List<PrioritizedHandler> resolved = new ArrayList<>();
            for (Map.Entry<Class<? extends Event>, List<PrioritizedHandler>> entry : handlers.entrySet()) {
                // 与旧的 isInstance(event) 全表扫描等价：事件是绑定类型的实例（含子类/接口实现）。
                if (entry.getKey().isAssignableFrom(eventClass)) {
                    resolved.addAll(entry.getValue());
                }
            }
            // 有界缓存：达到上限后不再缓存新事件类。已缓存类型仍 O(1)；
            // 新类型每次派发只扫一遍 handlers，而 handlers 数量远小于事件种类。
            if (lookupCache.size() < MAX_CACHED_EVENT_CLASSES) {
                List<PrioritizedHandler> previous = lookupCache.putIfAbsent(eventClass, resolved);
                if (previous != null) {
                    return previous;
                }
            }
            return resolved;
        }

        /** 携带绑定参数（优先级 / 是否接收已取消事件）的处理器条目。 */
        private record PrioritizedHandler(EventPriority priority, boolean receiveCancelled, Consumer<Event> handler) {}

        /** 每个优先级一个槽类；@SubscribeEvent 的 priority 只能写死在各自的注解里。 */
        private abstract static class PrioritySlot {
            final ForgeEventDispatcher dispatcher;

            PrioritySlot(ForgeEventDispatcher dispatcher) {
                this.dispatcher = dispatcher;
            }

            /** Forge 总线回调入口，注解决定调用时机（优先级 + receiveCanceled）。 */
            public abstract void onForgeEvent(Event event);
        }

        private static final class HighestSlot extends PrioritySlot {
            HighestSlot(ForgeEventDispatcher dispatcher) {
                super(dispatcher);
            }

            @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
            @Override
            public void onForgeEvent(Event event) {
                dispatcher.dispatch(event, EventPriority.HIGHEST);
            }
        }

        private static final class HighSlot extends PrioritySlot {
            HighSlot(ForgeEventDispatcher dispatcher) {
                super(dispatcher);
            }

            @SubscribeEvent(priority = EventPriority.HIGH, receiveCanceled = true)
            @Override
            public void onForgeEvent(Event event) {
                dispatcher.dispatch(event, EventPriority.HIGH);
            }
        }

        private static final class NormalSlot extends PrioritySlot {
            NormalSlot(ForgeEventDispatcher dispatcher) {
                super(dispatcher);
            }

            @SubscribeEvent(priority = EventPriority.NORMAL, receiveCanceled = true)
            @Override
            public void onForgeEvent(Event event) {
                dispatcher.dispatch(event, EventPriority.NORMAL);
            }
        }

        private static final class LowSlot extends PrioritySlot {
            LowSlot(ForgeEventDispatcher dispatcher) {
                super(dispatcher);
            }

            @SubscribeEvent(priority = EventPriority.LOW, receiveCanceled = true)
            @Override
            public void onForgeEvent(Event event) {
                dispatcher.dispatch(event, EventPriority.LOW);
            }
        }

        private static final class LowestSlot extends PrioritySlot {
            LowestSlot(ForgeEventDispatcher dispatcher) {
                super(dispatcher);
            }

            @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
            @Override
            public void onForgeEvent(Event event) {
                dispatcher.dispatch(event, EventPriority.LOWEST);
            }
        }
    }
}
