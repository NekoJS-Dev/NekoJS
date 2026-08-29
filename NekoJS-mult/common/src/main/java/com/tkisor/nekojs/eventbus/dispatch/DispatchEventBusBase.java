package com.tkisor.nekojs.eventbus.dispatch;

import com.tkisor.nekojs.eventbus.CommonPriority;
import com.tkisor.nekojs.api.event.EventBus;
import com.tkisor.nekojs.api.event.EventListenerToken;
import com.tkisor.nekojs.api.event.DispatchKey;
import com.tkisor.nekojs.eventbus.EventBusBase;
import com.tkisor.nekojs.eventbus.EventListenerTokenImpl;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
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

    public final Class<EVENT> eventType() {
        return mainBus.eventType();
    }

    public final DispatchKey<EVENT, KEY> dispatchKey() {
        return dispatchKey;
    }

    public final Set<KEY> registeredKeys() {
        return Set.copyOf(dispatched.keySet());
    }

    protected abstract BUS createBus(Class<EVENT> eventType, KEY key);

    public final EventListenerToken<EVENT> listen(KEY key, byte priority, Consumer<EVENT> listener) {
        if (key == null) {
            return mainBus.listen(priority, listener);
        }
        return this.dispatched
            .computeIfAbsent(key, k -> createBus(this.eventType(), k))
            .listen(priority, listener);
    }

    public final EventListenerToken<EVENT> listen(KEY key, Consumer<EVENT> listener) {
        return listen(key, CommonPriority.NORMAL, listener);
    }

    public final EventListenerToken<EVENT> listen(Consumer<EVENT> listener) {
        return listen(null, CommonPriority.NORMAL, listener);
    }

    public final EventListenerToken<EVENT> listen(byte priority, Consumer<EVENT> listener) {
        return listen(null, priority, listener);
    }

    public final boolean unregister(EventListenerToken<EVENT> token) {
        var impl = (EventListenerTokenImpl<EVENT, ?>) token;
        Object tokenKey = impl.key();
        if (tokenKey == null) {
            return mainBus.unregister(impl);
        }

        @SuppressWarnings("unchecked")
        var key = (KEY) tokenKey;

        var bus = this.dispatched.get(key);
        var result = bus != null && bus.unregister(token);
        if (result && bus.isEmpty()) {
            this.dispatched.remove(key);
        }
        return result;
    }

    /**
     * Post an event to the main (non-keyed) listeners first, then to the keyed
     * listeners registered for {@code key}.
     *
     * <p><b>Precedence:</b> {@link #mainBus} listeners always run before keyed
     * listeners. mainBus listeners are invoked via {@code mainBus.post(event)}.
     *
     * <p><b>Cancellation short-circuit:</b> if this bus is cancellable and
     * {@code mainBus.post(event)} returns {@code true} (a mainBus listener
     * cancelled the event), the keyed listeners for {@code key} are skipped and
     * this method returns {@code true} immediately. Keyed listeners only run when
     * mainBus did not cancel the event. For non-cancellable buses
     * {@code mainBus.post(event)} always returns {@code false}, so keyed listeners
     * always run.
     *
     * @return {@code true} if a listener cancelled the event, {@code false}
     *         otherwise (for non-cancellable buses this is always {@code false})
     */
    public final boolean post(EVENT event, KEY key) {
        if (mainBus.post(event)) {
            return true;
        }

        if (key != null) {
            var dispatchedBus = this.dispatched.get(key);
            return dispatchedBus != null && dispatchedBus.post(event);
        }

        return false;
    }

    /**
     * Post an event, deriving the dispatch key from the event via
     * {@link #dispatchKey}.
     *
     * <p><b>Precedence:</b> {@link #mainBus} listeners always run first, then the
     * keyed listeners for the derived key — see {@link #post(Object, Object)}.
     *
     * <p><b>Cancellation short-circuit:</b> if this bus is cancellable and a
     * mainBus listener cancels the event, the keyed listeners are skipped.
     *
     * <p>If no keyed listeners are registered at all ({@link #dispatched} empty),
     * dispatching is skipped and only mainBus is posted.
     */
    public final boolean post(EVENT event) {
        if (this.dispatched.isEmpty()) {
            // skip dispatching if there's no registered dispatched listener
            return mainBus.post(event);
        }
        return post(event, this.dispatchKey.eventToKey(event));
    }
}
