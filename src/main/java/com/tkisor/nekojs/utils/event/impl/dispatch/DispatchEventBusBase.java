package com.tkisor.nekojs.utils.event.impl.dispatch;

import com.tkisor.nekojs.utils.event.CommonPriority;
import com.tkisor.nekojs.utils.event.EventBus;
import com.tkisor.nekojs.utils.event.EventListenerToken;
import com.tkisor.nekojs.utils.event.dispatch.DispatchKey;
import com.tkisor.nekojs.utils.event.impl.EventBusBase;
import com.tkisor.nekojs.utils.event.impl.EventListenerTokenImpl;

import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * @author ZZZank
 */
abstract class DispatchEventBusBase<EVENT, KEY, BUS extends EventBusBase<EVENT, ?> & EventBus<EVENT>> {
    private final DispatchKey<EVENT, KEY> dispatchKey;
    protected final BUS mainBus;
    protected final Map<KEY, BUS> dispatched;

    protected DispatchEventBusBase(
        Class<EVENT> eventType,
        DispatchKey<EVENT, KEY> dispatchKey,
        Map<KEY, BUS> dispatched
    ) {
        this.dispatchKey = Objects.requireNonNull(dispatchKey);
        this.mainBus = createBus(eventType, null);
        this.dispatched = Objects.requireNonNull(dispatched);
    }

    public Class<EVENT> eventType() {
        return mainBus.eventType();
    }

    public DispatchKey<EVENT, KEY> dispatchKey() {
        return dispatchKey;
    }

    protected abstract BUS createBus(Class<EVENT> eventType, KEY key);

    public EventListenerToken<EVENT> listen(KEY key, byte priority, Consumer<EVENT> listener) {
        if (key == null) {
            return mainBus.listen(priority, listener);
        }
        return this.dispatched
            .computeIfAbsent(key, k -> createBus(this.eventType(), k))
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
        var impl = (EventListenerTokenImpl<EVENT, ?>) token;
        if (impl.key() == null) {
            return mainBus.unregister(impl);
        }

        @SuppressWarnings("unchecked")
        var key = (KEY) impl.key().get();

        var bus = this.dispatched.get(key);
        var result = bus != null && bus.unregister(token);
        if (result && bus.isEmpty()) {
            this.dispatched.remove(key);
        }
        return result;
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
