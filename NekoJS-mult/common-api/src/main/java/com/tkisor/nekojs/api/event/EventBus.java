package com.tkisor.nekojs.api.event;

import java.util.function.Consumer;

/**
 * @author ZZZank
 */
public interface EventBus<E> {

    Class<E> eventType();

    EventListenerToken<E> listen(Consumer<E> listener);

    EventListenerToken<E> listen(byte priority, Consumer<E> listener);

    /// @return always `false` for non-cancellable event bus
    /// @see CancellableEventBus#post(Object)
    boolean post(E event);

    /// @return `true` if there's a registered listener matching this token, `false` otherwise
    boolean unregister(EventListenerToken<E> token);

    // 自类型收窄：E_ extends E，运行时类型不变，仅擦除层面的强制转换
    @SuppressWarnings("unchecked")
    default <E_ extends E> EventBus<E_> cast() {
        return (EventBus<E_>) this;
    }
}
