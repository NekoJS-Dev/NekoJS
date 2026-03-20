package com.tkisor.nekojs.utils.event.impl;

import com.tkisor.nekojs.utils.event.CommonPriority;
import com.tkisor.nekojs.utils.event.EventListenerToken;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * @author ZZZank
 */
public abstract class EventBusBase<EVENT, LISTENER> {
    private final Class<EVENT> eventType;
    private final List<EventListenerTokenImpl<EVENT, LISTENER>> tokens;
    private volatile LISTENER built;

    protected EventBusBase(Class<EVENT> eventType) {
        this.eventType = Objects.requireNonNull(eventType);
        this.tokens = new ArrayList<>();
    }

    public final Class<EVENT> eventType() {
        return eventType;
    }

    public final EventListenerToken<EVENT> listen(LISTENER listener) {
        return listen(CommonPriority.NORMAL, listener);
    }

    public final EventListenerToken<EVENT> listen(byte priority, LISTENER listener) {
        built = null;
        var token = new EventListenerTokenImpl<>(eventType, priority, listener);
        tokens.add(token);
        return token;
    }

    public final boolean unregister(EventListenerToken<EVENT> token) {
        var changed = token instanceof EventListenerTokenImpl<?, ?> && this.tokens.remove(token);
        if (changed) {
            built = null;
        }
        return changed;
    }

    protected final LISTENER getBuilt(Function<Stream<LISTENER>, LISTENER> listenerCompiler) {
        if (built == null) {
            synchronized (this) {
                if (built == null) {
                    tokens.sort(null);
                    built = listenerCompiler.apply(tokens.stream().map(EventListenerTokenImpl::listener));
                }
            }
        }
        return built;
    }
}
