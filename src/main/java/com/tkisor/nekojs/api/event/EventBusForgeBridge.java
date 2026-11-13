//? if neoforge {
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
 * Wires an {@link IEventBus} to an {@link EventBusJS}.
 * <p>
 * When {@code .bind(...)} or {@code .bindTransformed(...)} is called, the {@code EventBusJS}
 * registers itself as an event listener on the EventBus supplied to {@link #create(IEventBus)}
 * when the instance is created. After {@code .bind(...)} there is no need to publish events to
 * the EventBusJS manually.
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
     * Binds an explicitly cancellable bus and writes the script's cancellation result back to the
     * native event through {@code cancelAction}.
     *
     * <p>It is meant for bus families whose native event does not implement
     * {@link ICancellableEvent} but offers an equivalent cancel lever (for example
     * {@code setNewDamage(0)} on {@code LivingDamageEvent.Pre}): when a listener returns
     * {@code true} the bus post returns true, and this method turns that result into a write on the
     * native event. The underlying bus of {@code busJS} must be cancellable; otherwise cancellation
     * is never visible (a silent no-op), and binding fails right away.
     */
    public <E extends Event> EventBusForgeBridge bindCancellable(EventBusJS<E, ?> busJS, Consumer<E> cancelAction) {
        Objects.requireNonNull(cancelAction, "cancelAction");
        if (!(busJS.bus() instanceof CancellableEventBus)) {
            throw new IllegalArgumentException(
                    "bindCancellable requires a cancellable bus: " + busJS.eventType().getName());
        }
        Consumer<E> listener = event -> {
            if (busJS.post(event)) {
                cancelAction.accept(event);
            }
        };
        return bindImpl(busJS.eventType(), listener, EventPriority.NORMAL, false);
    }

    /**
     * Binds a NekoJS bus with a predicate filter: a native event is delivered to scripts only when it
     * passes {@code filter}.
     *
     * <p>It is meant for dual-side events ({@code PlayerTickEvent}/{@code LevelTickEvent}/
     * {@code EntityTickEvent}/{@code EntityJoinLevelEvent}/interaction events and so on) bound to the
     * SERVER bus. On an integrated server these events fire a client instance on the render thread,
     * and entering a SERVER script Context from there trips Graal's multi-threaded access rejection
     * ({@code Multi threaded access ... is not allowed}). The convention is that the SERVER bus only
     * delivers server instances ({@code !level().isClientSide()}); client-side events belong to the
     * CLIENT bus. The filter only decides whether to deliver to scripts; it does not cancel the
     * filtered-out native event.
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
//?}
