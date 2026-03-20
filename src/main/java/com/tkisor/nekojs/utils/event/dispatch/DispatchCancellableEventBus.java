package com.tkisor.nekojs.utils.event.dispatch;

import com.tkisor.nekojs.utils.event.CancellableEventBus;
import com.tkisor.nekojs.utils.event.EventListenerToken;
import com.tkisor.nekojs.utils.event.impl.dispatch.DispatchCancellableEventBusImpl;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * @author ZZZank
 */
public interface DispatchCancellableEventBus<E, K> extends CancellableEventBus<E>, DispatchEventBus<E, K> {

    static <E, K> DispatchCancellableEventBus<E, K> create(Class<E> eventType, DispatchKey<E, K> dispatchKey) {
        return new DispatchCancellableEventBusImpl<>(eventType, dispatchKey, new ConcurrentHashMap<>());
    }

    EventListenerToken<E> listen(K key, byte priority, Predicate<E> listener);

    EventListenerToken<E> listen(K key, Predicate<E> listener);

    @Override
    default <E_ extends E, K_ extends K> DispatchCancellableEventBus<E_, K_> castDispatch() {
        return (DispatchCancellableEventBus<E_, K_>) this;
    }
}
