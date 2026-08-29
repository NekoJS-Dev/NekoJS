package com.tkisor.nekojs.api.event;

import java.util.Set;
import java.util.function.Consumer;

/**
 * @author ZZZank
 */
public interface DispatchEventBus<E, K> extends EventBus<E> {

    DispatchKey<E, K> dispatchKey();

    EventListenerToken<E> listen(K key, byte priority, Consumer<E> listener);

    EventListenerToken<E> listen(K key, Consumer<E> listener);

    boolean post(E event, K key);

    /** 已注册监听的定向 key 集合（例如 lang 事件的语言代码）。 */
    Set<K> registeredKeys();

    // 自类型收窄：E_ extends E、K_ extends K，运行时类型不变，仅擦除层面的强制转换
    @SuppressWarnings("unchecked")
    default <E_ extends E, K_ extends K> DispatchEventBus<E_, K_> castDispatch() {
        return (DispatchEventBus<E_, K_>) this;
    }
}
