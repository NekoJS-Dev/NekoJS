package com.tkisor.nekojs.api.event;

import java.util.function.Predicate;

/**
 * @author ZZZank
 */
public interface CancellableEventBus<E> extends EventBus<E> {

    EventListenerToken<E> listen(Predicate<E> listener);

    EventListenerToken<E> listen(byte priority, Predicate<E> listener);

    /// @return `true` if there's listener that returns `true`, `false` otherwise
    @Override
    boolean post(E event);

    @Override
    default <E_ extends E> CancellableEventBus<E_> cast() {
        return (CancellableEventBus<E_>) this;
    }
}
