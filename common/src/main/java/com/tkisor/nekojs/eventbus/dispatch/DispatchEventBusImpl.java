package com.tkisor.nekojs.eventbus.dispatch;

import com.tkisor.nekojs.api.event.DispatchEventBus;
import com.tkisor.nekojs.api.event.DispatchKey;
import com.tkisor.nekojs.eventbus.EventBusImpl;

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
