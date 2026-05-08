package com.tkisor.nekojs.utils.event.dispatch;

import com.tkisor.nekojs.utils.event.EventBus;
import com.tkisor.nekojs.utils.event.EventListenerToken;
import com.tkisor.nekojs.utils.event.impl.dispatch.DispatchEventBusImpl;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * @author ZZZank
 */
public interface DispatchEventBus<E, K> extends EventBus<E> {

    static <E, K> DispatchEventBus<E, K> create(Class<E> eventType, DispatchKey<E, K> dispatchKey) {
        return new DispatchEventBusImpl<>(eventType, dispatchKey, new ConcurrentHashMap<>());
    }

    DispatchKey<E, K> dispatchKey();

    EventListenerToken<E> listen(K key, byte priority, Consumer<E> listener);

    EventListenerToken<E> listen(K key, Consumer<E> listener);

    boolean post(E event, K key);

    default <E_ extends E, K_ extends K> DispatchEventBus<E_, K_> castDispatch() {
        return (DispatchEventBus<E_, K_>) this;
    }
}
