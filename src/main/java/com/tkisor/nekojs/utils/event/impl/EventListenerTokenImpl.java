package com.tkisor.nekojs.utils.event.impl;

import com.tkisor.nekojs.utils.event.EventListenerToken;

import java.util.Objects;

/**
 * @author ZZZank
 */
public record EventListenerTokenImpl<EVENT, LISTENER>(
    Class<EVENT> eventType,
    byte priority,
    LISTENER listener
) implements EventListenerToken<EVENT>, Comparable<EventListenerTokenImpl<EVENT, LISTENER>> {

    public EventListenerTokenImpl(Class<EVENT> eventType, byte priority, LISTENER listener) {
        this.eventType = Objects.requireNonNull(eventType);
        this.priority = priority;
        this.listener = Objects.requireNonNull(listener);
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
