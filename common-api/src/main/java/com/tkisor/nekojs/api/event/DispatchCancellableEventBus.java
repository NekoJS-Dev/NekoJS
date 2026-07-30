package com.tkisor.nekojs.api.event;

import java.util.function.Predicate;

/**
 * @author ZZZank
 */
public interface DispatchCancellableEventBus<E, K> extends CancellableEventBus<E>, DispatchEventBus<E, K> {

    EventListenerToken<E> listen(K key, byte priority, Predicate<E> listener);

    EventListenerToken<E> listen(K key, Predicate<E> listener);

    @Override
    default <E_ extends E, K_ extends K> DispatchCancellableEventBus<E_, K_> castDispatch() {
        return (DispatchCancellableEventBus<E_, K_>) this;
    }
}
