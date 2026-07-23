package com.tkisor.nekojs.api.event;

import java.util.function.Consumer;

/**
 * @author ZZZank
 */
public interface DispatchEventBus<E, K> extends EventBus<E> {

    DispatchKey<E, K> dispatchKey();

    EventListenerToken<E> listen(K key, byte priority, Consumer<E> listener);

    EventListenerToken<E> listen(K key, Consumer<E> listener);

    boolean post(E event, K key);

    default <E_ extends E, K_ extends K> DispatchEventBus<E_, K_> castDispatch() {
        return (DispatchEventBus<E_, K_>) this;
    }
}
