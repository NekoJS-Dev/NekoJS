package com.tkisor.nekojs.utils.event.impl;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.core.error.NekoErrorTracker;
import com.tkisor.nekojs.utils.event.EventBus;
import org.graalvm.polyglot.PolyglotException;

import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.stream.Stream;

/**
 * @author ZZZank
 */
public final class EventBusImpl<E> extends EventBusBase<E, Consumer<E>> implements EventBus<E> {

    public EventBusImpl(Class<E> eventType) {
        super(eventType);
    }

    @Override
    public boolean post(E event) {
        // 临时的错误捕获方案，也许后续需要继续优化
        try {
            getBuilt(EventBusImpl::compile).accept(event);
        } catch (PolyglotException e) {
            NekoErrorTracker.recordEventError(e);
        } catch (Exception e) {
            NekoJS.LOGGER.error("EventBus执行异常: {}", e.getMessage(), e);
        }
        return false;
    }

    private static <E> Consumer<E> compile(Stream<Consumer<E>> consumerStream) {
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