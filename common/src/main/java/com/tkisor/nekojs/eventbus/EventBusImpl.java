package com.tkisor.nekojs.eventbus;

import com.tkisor.nekojs.api.event.EventBus;
import com.tkisor.nekojs.api.event.EventListenerToken;

import java.util.Set;

import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.stream.Stream;

/**
 * @author ZZZank
 */
public final class EventBusImpl<E> extends EventBusBase<E, Consumer<E>> implements EventBus<E> {

    public EventBusImpl(Class<E> eventType, Object key) {
        super(eventType, key);
    }

    @org.jetbrains.annotations.ApiStatus.Internal
    public void postSelected(E event, Set<EventListenerToken<E>> selected) {
        compile(orderedListeners(selected::contains)).accept(event);
    }

    @Override
    public boolean post(E event) {
        getBuilt(EventBusImpl::compile).accept(event);
        return false;
    }

    // A generic array cannot be created directly, so the array type cast passed to toArray is
    // unavoidable under erasure.
    @SuppressWarnings("unchecked")
    static <E> Consumer<E> compile(Stream<Consumer<E>> consumerStream) {
        var arr = consumerStream.toArray((IntFunction<Consumer<E>[]>) Consumer[]::new);
        switch (arr.length) {
            case 0:
                return (ignored) -> {};
            case 1:
                return arr[0];
            case 2:
                return arr[0].andThen(arr[1]);
            case 3:
                var c1 = arr[0];
                var c2 = arr[1];
                var c3 = arr[2];
                return event -> {
                    c1.accept(event);
                    c2.accept(event);
                    c3.accept(event);
                };
            default:
                return event -> {
                    for (var consumer : arr) {
                        consumer.accept(event);
                    }
                };
        }
    }
}