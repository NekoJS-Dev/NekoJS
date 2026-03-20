package com.tkisor.nekojs.utils.event.dispatch;

import com.tkisor.nekojs.utils.event.impl.dispatch.DispatchKeyImpl;

import java.util.function.Function;

/**
 * @author ZZZank
 */
public interface DispatchKey<E, K> {
    static <E, K> DispatchKey<E, K> of(Class<K> keyType, Function<E, K> toKey) {
        return new DispatchKeyImpl<>(keyType, toKey);
    }

    static <E, K> DispatchKey<E, K> of(Class<K> keyType) {
        return of(keyType, (ignored) -> null);
    }

    static <E> DispatchKey<E, String> string() {
        return of(String.class);
    }

    Class<K> keyType();

    K eventToKey(E event);
}
