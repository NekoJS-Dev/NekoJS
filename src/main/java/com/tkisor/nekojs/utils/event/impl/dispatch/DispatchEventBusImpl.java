package com.tkisor.nekojs.utils.event.impl.dispatch;

import com.tkisor.nekojs.utils.event.EventBus;
import com.tkisor.nekojs.utils.event.dispatch.DispatchKey;
import com.tkisor.nekojs.utils.event.dispatch.DispatchEventBus;

import java.util.Map;

/**
 * @author ZZZank
 */
public final class DispatchEventBusImpl<E, K> extends DispatchEventBusBase<E, K, EventBus<E>> implements DispatchEventBus<E, K> {
    public DispatchEventBusImpl(Class<E> eventType, DispatchKey<E, K> dispatchKey, Map<K, EventBus<E>> dispatched) {
        super(eventType, dispatchKey, dispatched);
    }

    @Override
    protected EventBus<E> createBus(Class<E> eventType) {
        return EventBus.create(eventType);
    }
}
