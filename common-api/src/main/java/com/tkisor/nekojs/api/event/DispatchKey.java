package com.tkisor.nekojs.api.event;

import java.util.function.Function;

/**
 * @author ZZZank
 */
public interface DispatchKey<E, K> {

    Class<K> keyType();

    K eventToKey(E event);
}
