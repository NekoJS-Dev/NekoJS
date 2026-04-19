package com.tkisor.nekojs.utils.event;

import com.tkisor.nekojs.utils.event.impl.EventBusImpl;

import java.util.function.Consumer;

/**
 * @author ZZZank
 */
public interface EventBus<E> {

    static <E> EventBus<E> create(Class<E> eventType) {
        return new EventBusImpl<>(eventType, null);
    }

    Class<E> eventType();

    EventListenerToken<E> listen(Consumer<E> listener);

    EventListenerToken<E> listen(byte priority, Consumer<E> listener);

    /// @return always `false` for non-cancellable event bus
    /// @see CancellableEventBus#post(Object)
    boolean post(E event);

    /// @return `true` if there's a registered listener matching this token, `false` otherwise
    boolean unregister(EventListenerToken<E> token);

    default <E_ extends E> EventBus<E_> cast() {
        return (EventBus<E_>) this;
    }
}
