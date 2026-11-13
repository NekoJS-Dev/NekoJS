package com.tkisor.nekojs.api.event;

import com.tkisor.nekojs.eventbus.dispatch.DispatchKeyImpl;

import java.util.function.Function;

/**
 * @author ZZZank
 */
public interface DispatchKey<E, K> {

    // The generic bound of keyType is guaranteed by the caller (EventBusJS/EventGroup); the Class
    // itself is erased
    @SuppressWarnings("unchecked")
    static <E, K> DispatchKey<E, K> of(Class<? super K> keyType, Function<? super E, K> toKey) {
        return new DispatchKeyImpl<>((Class<K>) keyType, toKey);
    }

    static <E, K> DispatchKey<E, K> of(Class<? super K> keyType) {
        return of(keyType, (ignored) -> null);
    }

    static <E> DispatchKey<E, String> string() {
        return of(String.class);
    }

    Class<K> keyType();

    K eventToKey(E event);
}
