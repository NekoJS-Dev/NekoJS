package com.tkisor.nekojs.api.event;

import java.util.function.Predicate;

/**
 * A dispatch (keyed) event bus that is also cancellable.
 *
 * <p>This interface intentionally combines the keyed {@code Consumer} overloads
 * inherited from {@link DispatchEventBus} with the keyed {@link Predicate}
 * overloads declared here:
 * <ul>
 * <li>{@code listen(K, Consumer<E>)} / {@code listen(K, byte, Consumer<E>)} —
 *     inherited: fire-and-forget keyed listeners that never cancel;</li>
 * <li>{@code listen(K, Predicate<E>)} / {@code listen(K, byte, Predicate<E>)} —
 *     declared here: cancellable keyed listeners whose boolean result decides
 *     cancellation.</li>
 * </ul>
 * The two shapes coexist so one keyed bus can carry both plain consumers and
 * cancelling predicates. Because {@code Consumer} and {@link Predicate} are
 * both single-argument functional interfaces, a bare lambda such as
 * {@code listen("key", event -> ...)} is ambiguous for Java plugin authors.
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
public interface DispatchCancellableEventBus<E, K> extends CancellableEventBus<E>, DispatchEventBus<E, K> {

    // suppress overloads: intentional dual Consumer/Predicate overloads; see class javadoc
    @SuppressWarnings("overloads")
    EventListenerToken<E> listen(K key, byte priority, Predicate<E> listener);

    // suppress overloads: intentional dual Consumer/Predicate overloads; see class javadoc
    @SuppressWarnings("overloads")
    EventListenerToken<E> listen(K key, Predicate<E> listener);

    @Override
    default <E_ extends E, K_ extends K> DispatchCancellableEventBus<E_, K_> castDispatch() {
        return (DispatchCancellableEventBus<E_, K_>) this;
    }
}
