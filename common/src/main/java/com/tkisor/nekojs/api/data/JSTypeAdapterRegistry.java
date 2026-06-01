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

    Collection<JSTypeAdapter<?>> view();

    final class Impl implements JSTypeAdapterRegistry {
        private final List<JSTypeAdapter<?>> adapters = new ArrayList<>();

        @Override
        public <T> void register(JSTypeAdapter<T> adapter) {
            adapters.add(Objects.requireNonNull(adapter, "adapter"));
        }

        @Override
        public Collection<JSTypeAdapter<?>> view() {
            return Collections.unmodifiableList(adapters);
        }
    }
}
