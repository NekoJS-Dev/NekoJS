package com.tkisor.nekojs.utils.event.impl.dispatch;

import com.tkisor.nekojs.utils.event.dispatch.DispatchKey;

import java.util.Objects;
import java.util.function.Function;

/**
 * @author ZZZank
 */
public record DispatchKeyImpl<E, K>(
    Class<K> keyType,
    Function<? super E, K> toKey
) implements DispatchKey<E, K> {

    public DispatchKeyImpl {
        Objects.requireNonNull(keyType);
        Objects.requireNonNull(toKey);
    }

    @Override
    public K eventToKey(E event) {
        return toKey.apply(event);
    }
}
