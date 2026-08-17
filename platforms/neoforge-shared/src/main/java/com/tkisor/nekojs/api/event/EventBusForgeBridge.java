package com.tkisor.nekojs.api.event;

import com.tkisor.nekojs.api.event.CancellableEventBus;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.bus.api.IEventBus;
import org.jspecify.annotations.NonNull;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * 用于将 {@link IEventBus} 与 {@link EventBusJS} 接驳在一起。
 * <p>
 * 在调用 {@code .bind(...)} 或 {@code .bindTransformed(...)} 时，{@code EventBusJS} 自身会作为一个事件监听注册到
 * {@link #create(IEventBus) 创建实例}时提供的 EventBus 中。因此调用 {@code .bind(...)} 之后 不 需 要 额外手动把事件发布到 EventBusJS
 *
 * @author ZZZank
 */
public class EventBusForgeBridge {
    public static EventBusForgeBridge create(IEventBus forgeBus) {
        return new EventBusForgeBridge(forgeBus);
    }

    private final IEventBus forgeBus;

    protected EventBusForgeBridge(IEventBus forgeBus) {
        this.forgeBus = Objects.requireNonNull(forgeBus);
    }

    public <E extends Event> EventBusForgeBridge bind(EventBusJS<E, ?> busJS, EventPriority priority, boolean receiveCancelled) {
        var bus = busJS.bus();
        var eventType = bus.eventType();

        Consumer<E> listener;
        if (bus instanceof CancellableEventBus<E> && ICancellableEvent.class.isAssignableFrom(eventType)) {
            // bus cancellable and event cancellable
            listener = new CancellableListener<>(busJS);
        } else {
            listener = new Listener<>(busJS);
        }

        return bindImpl(eventType, listener, priority, receiveCancelled);
    }

    private <E extends Event> EventBusForgeBridge bindImpl(
        Class<E> eventType,
        Consumer<E> listener,
        EventPriority priority,
        boolean receiveCancelled
    ) {
        forgeBus.addListener(priority, receiveCancelled, eventType, listener);
        return this;
    }

    public <E extends Event> EventBusForgeBridge bind(EventBusJS<E, ?> bus) {
        return bind(bus, EventPriority.NORMAL, false);
    }

    /**
     * 绑定一个带谓词过滤的 NekoJS 总线：仅当原生事件通过 {@code filter} 时才投递给脚本。
     *
     * <p>用于双逻辑侧事件（{@code PlayerTickEvent}/{@code LevelTickEvent}/
     * {@code EntityTickEvent}/{@code EntityJoinLevelEvent}/交互事件等）绑定到 SERVER 总线的场景：
     * 单人集成服上这些事件的客户端实例在 Render 线程触发，直接进入 SERVER 脚本 Context 会触发
     * Graal 的多线程访问拒绝（{@code Multi threaded access ... is not allowed}）。约定 SERVER
     * 总线只投递服务端实例（{@code !level().isClientSide()}），客户端侧事件属于 CLIENT 总线。
     * filter 仅决定是否投递给脚本，不会取消被过滤掉的原生事件本身。
     */
    public <E extends Event> EventBusForgeBridge bind(EventBusJS<E, ?> busJS, Predicate<E> filter) {
        return bind(busJS, filter, EventPriority.NORMAL, false);
    }

    public <E extends Event> EventBusForgeBridge bind(
        EventBusJS<E, ?> busJS,
        Predicate<E> filter,
        EventPriority priority,
        boolean receiveCancelled
    ) {
        Objects.requireNonNull(filter, "filter");
        var bus = busJS.bus();
        boolean cancellable = bus instanceof CancellableEventBus<E> && ICancellableEvent.class.isAssignableFrom(bus.eventType());
        Consumer<E> listener = event -> {
            if (!filter.test(event)) return;
            if (busJS.post(event) && event instanceof ICancellableEvent cancellableEvent) {
                cancellableEvent.setCanceled(true);
            }
        };
        return bindImpl(bus.eventType(), listener, priority, receiveCancelled);
    }

    public <E, E_FORGE extends Event> EventBusForgeBridge bindTransformed(
        EventBusJS<E, ?> busJS,
        Function<E_FORGE, E> transformer,
        Class<E_FORGE> eventType,
        EventPriority priority,
        boolean receiveCancelled
    ) {
        Objects.requireNonNull(busJS, "EventBusJS<E, ?> busJS == null");
        Objects.requireNonNull(transformer, "Function<E_FORGE, E> transformer == null");
        var bus = busJS.bus();

        Consumer<E_FORGE> listener;
        if (bus instanceof CancellableEventBus<E> && ICancellableEvent.class.isAssignableFrom(eventType)) {
            listener = new CancellableTransformedListener<>(busJS, transformer, eventType);
        } else {
            listener = new TransformedListener<>(busJS, transformer, eventType);
        }

        return bindImpl(eventType, listener, priority, receiveCancelled);
    }

    public <E, E_FORGE extends Event> EventBusForgeBridge bindTransformed(
        EventBusJS<E, ?> bus,
        Function<E_FORGE, E> transformer,
        Class<E_FORGE> eventType
    ) {
        return bindTransformed(bus, transformer, eventType, EventPriority.NORMAL, false);
    }

    private record CancellableListener<E extends Event>(EventBusJS<E, ?> busJS) implements Consumer<E> {

        @Override
        public void accept(E event) {
            if (busJS.post(event)) {
                ((ICancellableEvent) event).setCanceled(true);
            }
        }

        @Override
        public @NonNull String toString() {
            return String.format("CancellableListener(%s)", busJS.bus().eventType().getName());
        }
    }

    private record Listener<E extends Event>(EventBusJS<E, ?> busJS) implements Consumer<E> {

        @Override
        public void accept(E event) {
            busJS.post(event);
        }

        @Override
        public @NonNull String toString() {
            return String.format("Listener(%s)", busJS.bus().eventType().getName());
        }
    }

    private record CancellableTransformedListener<E, E_FORGE extends Event>(
        EventBusJS<E, ?> busJS,
        Function<E_FORGE, E> transformer,
        Class<E_FORGE> eventType
    ) implements Consumer<E_FORGE> {

        @Override
        public void accept(E_FORGE e) {
            if (busJS.post(transformer.apply(e))) {
                ((ICancellableEvent) e).setCanceled(true);
            }
        }

        @Override
        public @NonNull String toString() {
            return String.format("CancellableListener(%s->%s)", busJS.bus().eventType().getName(), eventType.getName());
        }
    }

    private record TransformedListener<E, E_FORGE extends Event>(
        EventBusJS<E, ?> busJS,
        Function<E_FORGE, E> transformer,
        Class<E_FORGE> eventType
    ) implements Consumer<E_FORGE> {

        @Override
        public void accept(E_FORGE e) {
            busJS.post(transformer.apply(e));
        }

        @Override
        public @NonNull String toString() {
            return String.format("Listener(%s->%s)", busJS.bus().eventType().getName(), eventType.getName());
        }
    }
}