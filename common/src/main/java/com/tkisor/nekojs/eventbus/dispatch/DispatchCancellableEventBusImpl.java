package com.tkisor.nekojs.eventbus.dispatch;

import com.tkisor.nekojs.eventbus.CommonPriority;
import com.tkisor.nekojs.api.event.EventListenerToken;
import com.tkisor.nekojs.api.event.DispatchCancellableEventBus;
import com.tkisor.nekojs.api.event.DispatchKey;
import com.tkisor.nekojs.eventbus.CancellableEventBusImpl;

import java.util.Map;
import java.util.function.Predicate;

/**
 * Default implementation of {@link DispatchCancellableEventBus}.
 *
 * <p>This class intentionally exposes both keyed and non-keyed {@code listen}
 * overload shapes:
 * <ul>
 * <li>{@code Consumer} listeners ({@code listen(Consumer<E>)},
 *     {@code listen(byte, Consumer<E>)}, {@code listen(K, Consumer<E>)},
 *     {@code listen(K, byte, Consumer<E>)}) are inherited from
 *     {@code DispatchEventBusBase}: fire-and-forget listeners that never
 *     cancel;</li>
 * <li>{@link Predicate} listeners ({@code listen(Predicate<E>)},
 *     {@code listen(byte, Predicate<E>)}, {@code listen(K, Predicate<E>)},
 *     {@code listen(K, byte, Predicate<E>)}) are declared here: cancellable
 *     listeners whose boolean result decides cancellation.</li>
 * </ul>
 * The two shapes coexist so one bus can carry both plain consumers and
 * cancelling predicates. Because {@code Consumer} and {@code Predicate} are
 * both single-argument functional interfaces, a bare lambda such as
 * {@code listen(key, event -> ...)} is ambiguous for Java plugin authors.
 *
 * <p>Recommended unambiguous call forms:
 * <ul>
 * <li>Consumer listener: {@code listen(key, (E event) -> { ... })} — a block
 *     body with no return value;</li>
 * <li>Predicate listener: {@code listen(key, (E event) -> true/false)} — a
 *     boolean expression body;</li>
 * <li>or explicit casts / typed lambda blocks, e.g.
 *     {@code listen(key, (Consumer<E>) event -> doSomething(event))} and
 *     {@code listen(key, (Predicate<E>) event -> shouldCancel(event))}.</li>
 * </ul>
 *
 * <p>These APIs are primarily invoked from JS via {@code EventBusJS}, where
 * Java lambda overload resolution does not apply.
 *
 * @author ZZZank
 */
public final class DispatchCancellableEventBusImpl<E, K> extends DispatchEventBusBase<E, K, CancellableEventBusImpl<E>>
    implements DispatchCancellableEventBus<E, K> {

    public DispatchCancellableEventBusImpl(
        Class<E> eventType,
        DispatchKey<E, K> dispatchKey,
        Map<K, CancellableEventBusImpl<E>> dispatched
    ) {
        super(eventType, dispatchKey, dispatched);
    }

    @Override
    protected CancellableEventBusImpl<E> createBus(Class<E> eventType, K key) {
        return new CancellableEventBusImpl<>(eventType, key);
    }

    // suppress overloads: intentional dual Consumer/Predicate overloads; see class javadoc
    @SuppressWarnings("overloads")
    @Override
    public EventListenerToken<E> listen(K key, byte priority, Predicate<E> listener) {
        if (key == null) {
            return mainBus.listen(priority, listener);
        }
        return this.dispatched
            .computeIfAbsent(key, k -> this.createBus(this.eventType(), k))
            .listen(priority, listener);
    }

    // suppress overloads: intentional dual Consumer/Predicate overloads; see class javadoc
    @SuppressWarnings("overloads")
    @Override
    public EventListenerToken<E> listen(K key, Predicate<E> listener) {
        return listen(key, CommonPriority.NORMAL, listener);
    }

    // suppress overloads: intentional dual Consumer/Predicate overloads; see class javadoc
    @SuppressWarnings("overloads")
    @Override
    public EventListenerToken<E> listen(byte priority, Predicate<E> listener) {
        return listen(null, priority, listener);
    }

    // suppress overloads: intentional dual Consumer/Predicate overloads; see class javadoc
    @SuppressWarnings("overloads")
    @Override
    public EventListenerToken<E> listen(Predicate<E> listener) {
        return listen(null, CommonPriority.NORMAL, listener);
    }
}
