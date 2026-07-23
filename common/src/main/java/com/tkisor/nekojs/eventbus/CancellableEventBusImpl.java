package com.tkisor.nekojs.eventbus;

import com.tkisor.nekojs.api.event.CancellableEventBus;
import com.tkisor.nekojs.eventbus.CommonPriority;
import com.tkisor.nekojs.api.event.EventListenerToken;

import java.util.Arrays;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * @author ZZZank
 */
public final class CancellableEventBusImpl<E>
    extends EventBusBase<E, Predicate<E>> implements CancellableEventBus<E> {

    public CancellableEventBusImpl(Class<E> eventType, Object key) {
        super(eventType, key);
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
        var arr = predicateStream.toArray((IntFunction<Predicate<E>[]>) Predicate[]::new);
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
        }
        if (Arrays.stream(arr).allMatch(NeverCancelListener.class::isInstance)) {
            var consumer = EventBusImpl.compile(
                Arrays.stream(arr)
                    .map(pred -> ((NeverCancelListener<E>) pred).consumer())
            );
            return new NeverCancelListener<>(consumer);
        }
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