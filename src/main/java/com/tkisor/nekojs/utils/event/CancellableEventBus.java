package com.tkisor.nekojs.utils.event;

import com.tkisor.nekojs.utils.event.impl.CancellableEventBusImpl;

import java.util.function.Predicate;

/**
 * @author ZZZank
 */
public interface CancellableEventBus<E> extends EventBus<E> {

    static <E> CancellableEventBus<E> create(Class<E> eventType) {
        return new CancellableEventBusImpl<>(eventType);
    }

    EventListenerToken<E> listen(Predicate<E> listener);

    EventListenerToken<E> listen(byte priority, Predicate<E> listener);

    @Override
    default <E_ extends E> CancellableEventBus<E_> cast() {
        return (CancellableEventBus<E_>) this;
    }
}
