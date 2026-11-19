package com.tkisor.nekojs.eventbus.dispatch;

import com.tkisor.nekojs.api.event.DispatchEventBus;
import com.tkisor.nekojs.api.event.DispatchKey;
import com.tkisor.nekojs.api.event.EventListenerToken;
import com.tkisor.nekojs.eventbus.EventBusImpl;

import java.util.Map;
import java.util.Set;

/**
 * @author ZZZank
 */
public final class DispatchEventBusImpl<E, K> extends DispatchEventBusBase<E, K, EventBusImpl<E>> implements DispatchEventBus<E, K> {
    public DispatchEventBusImpl(Class<E> eventType, DispatchKey<E, K> dispatchKey, Map<K, EventBusImpl<E>> dispatched) {
        super(eventType, dispatchKey, dispatched);
    }

    @org.jetbrains.annotations.ApiStatus.Internal
    public void postSelected(E event, K key, Set<EventListenerToken<E>> selected) {
        mainBus.postSelected(event, selected);
        var keyedBus = key == null ? null : dispatched.get(key);
        if (keyedBus != null) keyedBus.postSelected(event, selected);
    }

    @Override
    protected EventBusImpl<E> createBus(Class<E> eventType, K key) {
        return new EventBusImpl<>(eventType, key);
    }
}
