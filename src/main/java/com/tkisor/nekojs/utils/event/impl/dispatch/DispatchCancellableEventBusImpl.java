package com.tkisor.nekojs.utils.event.impl.dispatch;

import com.tkisor.nekojs.utils.event.CommonPriority;
import com.tkisor.nekojs.utils.event.EventListenerToken;
import com.tkisor.nekojs.utils.event.dispatch.DispatchCancellableEventBus;
import com.tkisor.nekojs.utils.event.dispatch.DispatchKey;
import com.tkisor.nekojs.utils.event.impl.CancellableEventBusImpl;

import java.util.Map;
import java.util.function.Predicate;

/**
 * @author ZZZank
 */
public final class DispatchCancellableEventBusImpl<E, K> extends DispatchEventBusBase<E, K, CancellableEventBusImpl<E>>
    implements DispatchCancellableEventBus<E, K> {

    public DispatchCancellableEventBusImpl(
        Class<E> eventType,
        DispatchKey<E, K> dispatchKey,
        Map<K, CancellableEventBusImpl<E>> dispatched
    ) {
        super(eventType, dispatchKey, dispatched);
    }

    @Override
    protected CancellableEventBusImpl<E> createBus(Class<E> eventType, K key) {
        return new CancellableEventBusImpl<>(eventType, key);
    }

    @Override
    public EventListenerToken<E> listen(K key, byte priority, Predicate<E> listener) {
        if (key == null) {
            return mainBus.listen(priority, listener);
        }
        return this.dispatched
            .computeIfAbsent(key, k -> this.createBus(this.eventType(), k))
            .listen(priority, listener);
    }

    @Override
    public EventListenerToken<E> listen(K key, Predicate<E> listener) {
        return listen(key, CommonPriority.NORMAL, listener);
    }

    @Override
    public EventListenerToken<E> listen(byte priority, Predicate<E> listener) {
        return listen(null, priority, listener);
    }

    @Override
    public EventListenerToken<E> listen(Predicate<E> listener) {
        return listen(null, CommonPriority.NORMAL, listener);
    }
}
