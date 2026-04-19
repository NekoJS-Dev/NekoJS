package com.tkisor.nekojs.utils.event.impl.dispatch;

import com.tkisor.nekojs.utils.event.dispatch.DispatchKey;
import com.tkisor.nekojs.utils.event.dispatch.DispatchEventBus;
import com.tkisor.nekojs.utils.event.impl.EventBusImpl;

import java.util.Map;

/**
 * @author ZZZank
 */
public final class DispatchEventBusImpl<E, K> extends DispatchEventBusBase<E, K, EventBusImpl<E>> implements DispatchEventBus<E, K> {
    public DispatchEventBusImpl(Class<E> eventType, DispatchKey<E, K> dispatchKey, Map<K, EventBusImpl<E>> dispatched) {
        super(eventType, dispatchKey, dispatched);
    }

    @Override
    protected EventBusImpl<E> createBus(Class<E> eventType, K key) {
        return new EventBusImpl<>(eventType, key);
    }
}
