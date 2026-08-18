package com.tkisor.nekojs.eventbus;

import com.tkisor.nekojs.api.event.CancellableEventBus;
import com.tkisor.nekojs.eventbus.CommonPriority;
import com.tkisor.nekojs.api.event.EventListenerToken;

import java.util.Arrays;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Default implementation of {@link CancellableEventBus}.
 *
 * <p>This class intentionally exposes both {@code listen} overload shapes:
 * <ul>
 * <li>{@code listen(Consumer<E>)} / {@code listen(byte, Consumer<E>)} —
 *     declared here: fire-and-forget listeners that never cancel;</li>
 * <li>{@code listen(Predicate<E>)} / {@code listen(byte, Predicate<E>)} —
 *     inherited from {@code EventBusBase<Predicate<E>>}: cancellable listeners
 *     whose boolean result decides cancellation.</li>
 * </ul>
 * The two shapes coexist so one bus can carry both plain consumers and
 * cancelling predicates. Because {@code Consumer} and {@code Predicate} are
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
public final class CancellableEventBusImpl<E>
    extends EventBusBase<E, Predicate<E>> implements CancellableEventBus<E> {

    public CancellableEventBusImpl(Class<E> eventType, Object key) {
        super(eventType, key);
    }

    // suppress overloads: intentional dual Consumer/Predicate overloads; see class javadoc
    @SuppressWarnings("overloads")
    @Override
    public EventListenerToken<E> listen(byte priority, Consumer<E> listener) {
        return listen(priority, new NeverCancelListener<>(listener));
    }

    // suppress overloads: intentional dual Consumer/Predicate overloads; see class javadoc
    @SuppressWarnings("overloads")
    @Override
    public EventListenerToken<E> listen(Consumer<E> listener) {
        return listen(CommonPriority.NORMAL, new NeverCancelListener<>(listener));
    }

    @Override
    public boolean post(E event) {
        return getBuilt(CancellableEventBusImpl::compile).test(event);
    }

    // 泛型数组无法直接创建，toArray 的数组类型强制转换是擦除层面的必然操作
    @SuppressWarnings("unchecked")
    private static <E> Predicate<E> compile(Stream<Predicate<E>> predicateStream) {
        var arr = predicateStream.toArray((IntFunction<Predicate<E>[]>) Predicate[]::new);
        switch (arr.length) {
            case 0:
                return (ignored) -> false;
            case 1:
                return arr[0];
            case 2:
                return arr[0].or(arr[1]);
            case 3:
                var p1 = arr[0];
                var p2 = arr[1];
                var p3 = arr[2];
                return event -> p1.test(event) || p2.test(event) || p3.test(event);
        }
        if (Arrays.stream(arr).allMatch(NeverCancelListener.class::isInstance)) {
            var consumer = EventBusImpl.compile(
                Arrays.stream(arr)
                    .map(pred -> ((NeverCancelListener<E>) pred).consumer())
            );
            return new NeverCancelListener<>(consumer);
        }
        return event -> {
            for (var predicate : arr) {
                if (predicate.test(event)) {
                    return true;
                }
            }
            return false;
        };
    }
}