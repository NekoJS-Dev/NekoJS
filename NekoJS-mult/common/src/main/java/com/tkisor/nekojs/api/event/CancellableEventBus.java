package com.tkisor.nekojs.api.event;

import java.util.function.Predicate;

/**
 * A cancellable variant of {@link EventBus} whose listeners are
 * {@link Predicate}s: a listener returning {@code true} cancels the event and
 * no further listeners are run.
 *
 * <p>This interface intentionally keeps both {@code listen} overload shapes:
 * <ul>
 * <li>{@code listen(Consumer<E>)} / {@code listen(byte, Consumer<E>)} —
 *     inherited from {@link EventBus}: fire-and-forget listeners that never
 *     cancel;</li>
 * <li>{@code listen(Predicate<E>)} / {@code listen(byte, Predicate<E>)} —
 *     declared here: cancellable listeners whose boolean result decides
 *     cancellation.</li>
 * </ul>
 * The two shapes coexist so one bus can carry both plain consumers and
 * cancelling predicates. Because {@code Consumer} and {@link Predicate} are
 * both single-argument functional interfaces, a bare lambda such as
 * {@code listen(event -> ...)} is ambiguous for Java plugin authors.
 *
 * <p>Recommended unambiguous call forms:
 * <ul>
 * <li>Consumer listener: {@code listen((E event) -> { ... })} — a block body
 *     with no return value;</li>
 * <li>Predicate listener: {@code listen((E event) -> true/false)} — a boolean
 *     expression body;</li>
 * <li>or explicit casts / typed lambda blocks, e.g.
 *     {@code listen((Consumer<E>) event -> doSomething(event))} and
 *     {@code listen((Predicate<E>) event -> shouldCancel(event))}.</li>
 * </ul>
 *
 * <p>These APIs are primarily invoked from JS via {@code EventBusJS}, where
 * Java lambda overload resolution does not apply.
 *
 * @author ZZZank
 */
public interface CancellableEventBus<E> extends EventBus<E> {

    // suppress overloads: intentional dual Consumer/Predicate overloads; see class javadoc
    @SuppressWarnings("overloads")
    EventListenerToken<E> listen(Predicate<E> listener);

    // suppress overloads: intentional dual Consumer/Predicate overloads; see class javadoc
    @SuppressWarnings("overloads")
    EventListenerToken<E> listen(byte priority, Predicate<E> listener);

    /// @return `true` if there's listener that returns `true`, `false` otherwise
    @Override
    boolean post(E event);

    // 自类型收窄：E_ extends E，运行时类型不变，仅擦除层面的强制转换
    @SuppressWarnings("unchecked")
    @Override
    default <E_ extends E> CancellableEventBus<E_> cast() {
        return (CancellableEventBus<E_>) this;
    }
}
