package com.tkisor.nekojs.api.event;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.core.error.NekoErrorTracker;
import com.tkisor.nekojs.utils.event.CancellableEventBus;
import com.tkisor.nekojs.utils.event.EventBus;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.bus.api.IEventBus;
import org.graalvm.polyglot.PolyglotException;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/**
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
            listener = event -> {
                if (busJS.post(event)) {
                    ((ICancellableEvent) event).setCanceled(true);
                }
            };
        } else {
            listener = busJS::post;
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
            listener = e -> {
                if (busJS.post(transformer.apply(e))) {
                    ((ICancellableEvent) e).setCanceled(true);
                }
            };
        } else {
            listener = e -> busJS.post(transformer.apply(e));
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
}