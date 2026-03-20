package com.tkisor.nekojs.utils.event.impl.dispatch;

import com.tkisor.nekojs.utils.event.CancellableEventBus;
import com.tkisor.nekojs.utils.event.CommonPriority;
import com.tkisor.nekojs.utils.event.EventListenerToken;
import com.tkisor.nekojs.utils.event.dispatch.DispatchCancellableEventBus;
import com.tkisor.nekojs.utils.event.dispatch.DispatchKey;

import java.util.Map;
import java.util.function.Predicate;

/**
 * @author ZZZank
 */
public final class DispatchCancellableEventBusImpl<E, K> extends DispatchEventBusBase<E, K, CancellableEventBus<E>>
    implements DispatchCancellableEventBus<E, K> {

    public DispatchCancellableEventBusImpl(
        Class<E> eventType,
        DispatchKey<E, K> dispatchKey,
        Map<K, CancellableEventBus<E>> dispatched
    ) {
        super(eventType, dispatchKey, dispatched);
    }

    @Override
    protected CancellableEventBus<E> createBus(Class<E> eventType) {
        return CancellableEventBus.create(eventType);
    }

    @Override
    public EventListenerToken<E> listen(K key, byte priority, Predicate<E> listener) {
        if (key == null) {
            return mainBus.listen(priority, listener);
        }
        return this.dispatched
            .computeIfAbsent(key, ignored -> this.createBus(this.eventType()))
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
