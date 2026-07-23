package com.tkisor.nekojs.eventbus;

import com.tkisor.nekojs.api.event.CancellableEventBus;
import com.tkisor.nekojs.api.event.DispatchCancellableEventBus;
import com.tkisor.nekojs.api.event.DispatchEventBus;
import com.tkisor.nekojs.api.event.DispatchKey;
import com.tkisor.nekojs.api.event.EventBus;
import com.tkisor.nekojs.eventbus.dispatch.DispatchCancellableEventBusImpl;
import com.tkisor.nekojs.eventbus.dispatch.DispatchEventBusImpl;
import com.tkisor.nekojs.eventbus.dispatch.DispatchKeyImpl;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory for creating event bus instances.
 * Lives in {@code eventbus/} package to avoid circular dependencies with impl classes.
 *
 * @author ZZZank
 */
public final class EventBusFactory {

    private EventBusFactory() {}

    public static <E> EventBus<E> createEventBus(Class<E> eventType) {
        return new EventBusImpl<>(eventType, null);
    }

    public static <E> CancellableEventBus<E> createCancellableEventBus(Class<E> eventType) {
        return new CancellableEventBusImpl<>(eventType, null);
    }

    public static <E, K> DispatchEventBus<E, K> createDispatchEventBus(
        Class<E> eventType,
        DispatchKey<E, K> dispatchKey
    ) {
        return new DispatchEventBusImpl<>(eventType, dispatchKey, new ConcurrentHashMap<>());
    }

    public static <E, K> DispatchCancellableEventBus<E, K> createDispatchCancellableEventBus(
        Class<E> eventType,
        DispatchKey<E, K> dispatchKey
    ) {
        return new DispatchCancellableEventBusImpl<>(eventType, dispatchKey, new ConcurrentHashMap<>());
    }

    public static <E, K> DispatchKey<E, K> createDispatchKey(Class<? super K> keyType, java.util.function.Function<? super E, K> toKey) {
        return new DispatchKeyImpl<>((Class<K>) keyType, toKey);
    }

    public static <E, K> DispatchKey<E, K> createDispatchKey(Class<? super K> keyType) {
        return createDispatchKey(keyType, (ignored) -> null);
    }

    public static <E> DispatchKey<E, String> createStringDispatchKey() {
        return createDispatchKey(String.class);
    }
}
