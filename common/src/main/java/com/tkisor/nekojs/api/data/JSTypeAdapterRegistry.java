package com.tkisor.nekojs.api.data;

import com.tkisor.nekojs.api.JSTypeAdapter;
import graal.graalvm.polyglot.Value;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * @author ZZZank
 */
public interface JSTypeAdapterRegistry {

    <T> void register(JSTypeAdapter<T> adapter);

    <T> void register(JsTypeAdapter<T> adapter);

    default <T> void register(Class<T> target, Predicate<Value> filter, Function<Value, T> converter) {
        record LambdaJSTypeAdapter<T>(
            Class<T> target,
            Predicate<Value> filter,
            Function<Value, T> converter
        ) implements JSTypeAdapter<T> {
            public LambdaJSTypeAdapter {
                Objects.requireNonNull(target, "target");
                Objects.requireNonNull(filter, "filter");
                Objects.requireNonNull(converter, "converter");
            }

            @Override
            public Class<T> getTargetClass() {
                return target;
            }

            @Override
            public boolean test(Value value) {
                return filter.test(value);
            }

            @Override
            public T apply(Value value) {
                return converter.apply(value);
            }
        }

        register(new LambdaJSTypeAdapter<>(target, filter, converter));
    }

    /**
     * Registers an alias: target type X reuses the adapter of source type Y, including its
     * input recognition, input shapes and precedence. The value is converted to Y first and
     * then passed through {@code converter} to become X.
     *
     * <p>The source adapter is resolved lazily on the first conversion, so registration order
     * does not matter. Alias chains are allowed; cycles are not.
     *
     * @throws IllegalArgumentException when {@code target == from}
     */
    default <F, T> void registerAlias(Class<T> target, Class<F> from, Function<F, T> converter) {
        register(new AliasJSTypeAdapter<>(target, from, converter, null, this));
    }

    /**
     * Same as {@link #registerAlias(Class, Class, Function)}, but sets the precedence
     * explicitly. Without it, the alias inherits the precedence of the source adapter.
     */
    default <F, T> void registerAlias(Class<T> target, Class<F> from, Function<F, T> converter,
                                      ConversionPrecedence precedence) {
        register(new AliasJSTypeAdapter<>(target, from, converter, precedence, this));
    }

    Collection<JSTypeAdapter<?>> view();

    final class Impl implements JSTypeAdapterRegistry {
        private final List<JSTypeAdapter<?>> adapters = new ArrayList<>();

        @Override
        public <T> void register(JSTypeAdapter<T> adapter) {
            adapters.add(Objects.requireNonNull(adapter, "adapter"));
        }

        @Override
        public <T> void register(JsTypeAdapter<T> adapter) {
            adapters.add(Objects.requireNonNull(
                new com.tkisor.nekojs.core.bridge.NewAdapterBridge<>(adapter), "adapter"));
        }

        @Override
        public Collection<JSTypeAdapter<?>> view() {
            return Collections.unmodifiableList(adapters);
        }
    }
}
