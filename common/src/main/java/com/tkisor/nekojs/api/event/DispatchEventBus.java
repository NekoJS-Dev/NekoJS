package com.tkisor.nekojs.api.event;

import com.tkisor.nekojs.eventbus.dispatch.DispatchEventBusImpl;

import java.util.Set;
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

    /** The set of keys that have a registered listener (for example the language codes of lang
     * events). */
    Set<K> registeredKeys();

    // Self-type narrowing: E_ extends E and K_ extends K. The runtime type does not change; this only
    // casts away the erasure
    @SuppressWarnings("unchecked")
    default <E_ extends E, K_ extends K> DispatchEventBus<E_, K_> castDispatch() {
        return (DispatchEventBus<E_, K_>) this;
    }
}
