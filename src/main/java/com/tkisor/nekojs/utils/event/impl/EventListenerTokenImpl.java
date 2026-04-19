package com.tkisor.nekojs.utils.event.impl;

import com.tkisor.nekojs.utils.event.EventListenerToken;

import java.lang.ref.WeakReference;
import java.util.Objects;

/**
 * @param key {@code null} if the bus who created this token is not a dispatched bus
 * @author ZZZank
 */
public record EventListenerTokenImpl<EVENT, LISTENER>(
    Class<EVENT> eventType,
    byte priority,
    LISTENER listener,
    WeakReference<Object> key
) implements EventListenerToken<EVENT>, Comparable<EventListenerTokenImpl<EVENT, LISTENER>> {

    public EventListenerTokenImpl {
        Objects.requireNonNull(eventType, "eventType == null");
        Objects.requireNonNull(listener, "listener == null");
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj;
    }

    @Override
    public int compareTo(EventListenerTokenImpl<EVENT, LISTENER> o) {
        // high priority -> invoke earlier -> smaller index in list
        return -Byte.compare(this.priority, o.priority);
    }
}
