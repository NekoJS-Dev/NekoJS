package com.tkisor.nekojs.api.event;

/**
 * @author ZZZank
 */
public interface EventListenerToken<E> {
    Class<E> eventType();

    byte priority();
}
