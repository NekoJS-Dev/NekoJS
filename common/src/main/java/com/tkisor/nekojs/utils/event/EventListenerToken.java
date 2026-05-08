package com.tkisor.nekojs.utils.event;

/**
 * @author ZZZank
 */
public interface EventListenerToken<E> {
    Class<E> eventType();

    byte priority();
}
