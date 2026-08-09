package com.tkisor.nekojs.eventbus;

import com.tkisor.nekojs.api.event.EventListenerToken;

import java.util.Objects;

/**
 * @param key {@code null} if the bus who created this token is not a dispatched bus.
 *             Held strongly (not as a {@link java.lang.ref.WeakReference}) so that
 *             {@link com.tkisor.nekojs.eventbus.dispatch.DispatchEventBusBase#unregister}
 *             can always recover the dispatch key: a weak ref would let the key be
 *             GC'd while the token still lives in a per-key child bus, orphaning the
 *             listener. The dispatch bus already retains the key in its
 *             {@code dispatched} map for the bus's lifetime, so a strong ref buys
 *             nothing extra and a weak ref breaks unregister.
 * @author ZZZank
 */
public record EventListenerTokenImpl<EVENT, LISTENER>(
    Class<EVENT> eventType,
    byte priority,
    LISTENER listener,
    Object key
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
