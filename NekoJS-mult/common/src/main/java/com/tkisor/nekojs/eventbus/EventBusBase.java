package com.tkisor.nekojs.eventbus;

import com.tkisor.nekojs.eventbus.CommonPriority;
import com.tkisor.nekojs.api.event.EventListenerToken;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * @author ZZZank
 */
public abstract class EventBusBase<EVENT, LISTENER> {
    private final Class<EVENT> eventType;
    // CopyOnWriteArrayList: {@code listen()}/{@code unregister()} run on the reload
    // thread while {@code getBuilt()}/{@code post()} iterate on the game tick thread.
    // A plain ArrayList could throw ConcurrentModificationException or expose stale
    // reads. This is a read-heavy / write-rare pattern, so COW is a good fit. Note:
    // the COW iterator does not support {@code remove()}; the only mutation paths
    // here are {@link #listen} ({@code add}) and {@link #unregister}
    // ({@code List.remove(index/object)}, not iterator removal), so this is safe.
    private final List<EventListenerTokenImpl<EVENT, LISTENER>> tokens;
    private final Object key;
    private volatile LISTENER built;

    protected EventBusBase(Class<EVENT> eventType, Object key) {
        this.eventType = Objects.requireNonNull(eventType);
        this.key = key;
        this.tokens = new CopyOnWriteArrayList<>();
    }

    public final Class<EVENT> eventType() {
        return eventType;
    }

    public final boolean isEmpty() {
        return tokens.isEmpty();
    }

    public final EventListenerToken<EVENT> listen(LISTENER listener) {
        return listen(CommonPriority.NORMAL, listener);
    }

    public final EventListenerToken<EVENT> listen(byte priority, LISTENER listener) {
        // The key is held STRONGLY on the token (no WeakReference): see
        // EventListenerTokenImpl javadoc. A weak ref would let the dispatch key be
        // GC'd while the token still lives in a per-key child bus, which makes
        // DispatchEventBusBase.unregister fall back to mainBus and always return false.
        var token = new EventListenerTokenImpl<>(eventType, priority, listener, key);
        tokens.add(token);
        // Invalidate the compiled snapshot under the same monitor as getBuilt's
        // recompile, so a concurrent post() never observes a stale non-null `built`
        // in the window between mutation and invalidation.
        synchronized (this) {
            built = null;
        }
        return token;
    }

    public final boolean unregister(EventListenerToken<EVENT> token) {
        var changed = token instanceof EventListenerTokenImpl<?, ?> && this.tokens.remove(token);
        if (changed) {
            synchronized (this) {
                built = null;
            }
        }
        return changed;
    }

    protected final LISTENER getBuilt(Function<Stream<LISTENER>, LISTENER> listenerCompiler) {
        // Capture into a local: the return must not re-read the volatile `built` field after
        // releasing the monitor, because a concurrent listen()/unregister() can invalidate it
        // (built = null) in the window between the synchronized block exit and the return.
        // Returning the local snapshot guarantees a non-null compiled listener.
        LISTENER snapshot = built;
        if (snapshot == null) {
            synchronized (this) {
                snapshot = built;
                if (snapshot == null) {
                    // Copy into a mutable list for sorting: CopyOnWriteArrayList's
                    // sort mutates in place and would otherwise copy the whole array,
                    // and we want a stable compile snapshot under the lock.
                    var sorted = new ArrayList<>(tokens);
                    sorted.sort(null);
                    snapshot = listenerCompiler.apply(sorted.stream().map(EventListenerTokenImpl::listener));
                    built = snapshot;
                }
            }
        }
        return snapshot;
    }
}
