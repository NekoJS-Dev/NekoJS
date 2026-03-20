package com.tkisor.nekojs.utils.event.impl;

import com.tkisor.nekojs.utils.event.CancellableEventBus;
import com.tkisor.nekojs.utils.event.CommonPriority;
import com.tkisor.nekojs.utils.event.EventListenerToken;

import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * @author ZZZank
 */
public final class CancellableEventBusImpl<E>
    extends EventBusBase<E, Predicate<E>> implements CancellableEventBus<E> {

    public CancellableEventBusImpl(Class<E> eventType) {
        super(eventType);
    }

    @Override
    public EventListenerToken<E> listen(byte priority, Consumer<E> listener) {
        return listen(priority, new NeverCancelListener<>(listener));
    }

    @Override
    public EventListenerToken<E> listen(Consumer<E> listener) {
        return listen(CommonPriority.NORMAL, new NeverCancelListener<>(listener));
    }

    @Override
    public boolean post(E event) {
        return getBuilt(CancellableEventBusImpl::compile).test(event);
    }

    private static <E> Predicate<E> compile(Stream<Predicate<E>> predicateStream) {
        var arr = (Predicate<E>[]) predicateStream.toArray(Predicate[]::new);
        switch (arr.length) {
            case 0:
                return (ignored) -> false;
            case 1:
                return arr[0];
            case 2:
                return arr[0].or(arr[1]);
            case 3:
                var p1 = arr[0];
                var p2 = arr[1];
                var p3 = arr[2];
                return event -> p1.test(event) || p2.test(event) || p3.test(event);
            default:
                return event -> {
                    for (var predicate : arr) {
                        if (predicate.test(event)) {
                            return true;
                        }
                    }
                    return false;
                };
        }
    }
}