package com.tkisor.nekojs.utils.event.impl.dispatch;

import com.tkisor.nekojs.utils.event.CommonPriority;
import com.tkisor.nekojs.utils.event.EventBus;
import com.tkisor.nekojs.utils.event.EventListenerToken;
import com.tkisor.nekojs.utils.event.dispatch.DispatchKey;

import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * @author ZZZank
 */
abstract class DispatchEventBusBase<EVENT, KEY, BUS extends EventBus<EVENT>> {
    private final DispatchKey<EVENT, KEY> dispatchKey;
    protected final BUS mainBus;
    protected final Map<KEY, BUS> dispatched;

    protected DispatchEventBusBase(
        Class<EVENT> eventType,
        DispatchKey<EVENT, KEY> dispatchKey,
        Map<KEY, BUS> dispatched
    ) {
        this.dispatchKey = Objects.requireNonNull(dispatchKey);
        this.mainBus = createBus(eventType);
        this.dispatched = Objects.requireNonNull(dispatched);
    }

    public Class<EVENT> eventType() {
        return mainBus.eventType();
    }

    public DispatchKey<EVENT, KEY> dispatchKey() {
        return dispatchKey;
    }

    protected abstract BUS createBus(Class<EVENT> eventType);

    public EventListenerToken<EVENT> listen(KEY key, byte priority, Consumer<EVENT> listener) {
        if (key == null) {
            return mainBus.listen(priority, listener);
        }
        return this.dispatched
            .computeIfAbsent(key, k -> createBus(this.eventType()))
            .listen(priority, listener);
    }

    public EventListenerToken<EVENT> listen(KEY key, Consumer<EVENT> listener) {
        return listen(key, CommonPriority.NORMAL, listener);
    }

    public EventListenerToken<EVENT> listen(Consumer<EVENT> listener) {
        return listen(null, CommonPriority.NORMAL, listener);
    }

    public EventListenerToken<EVENT> listen(byte priority, Consumer<EVENT> listener) {
        return listen(null, priority, listener);
    }

    public boolean unregister(EventListenerToken<EVENT> token) {
        if (mainBus.unregister(token)) {
            return true;
        }
        return this.dispatched.values()
            .stream()
            .anyMatch(bus -> bus.unregister(token));
    }

    public boolean post(EVENT event, KEY key) {
        if (mainBus.post(event)) {
            return true;
        }

        if (key != null) {
            var dispatchedBus = this.dispatched.get(key);
            return dispatchedBus != null && dispatchedBus.post(event);
        }

        return false;
    }

    public boolean post(EVENT event) {
        if (this.dispatched.isEmpty()) {
            // skip dispatching if there's no registered dispatched listener
            return mainBus.post(event);
        }
        return post(event, this.dispatchKey.eventToKey(event));
    }
}
