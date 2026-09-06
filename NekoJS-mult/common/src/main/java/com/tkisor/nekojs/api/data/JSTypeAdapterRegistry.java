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
     * alias 注册：目标类型 X 复用来源类型 Y 的适配器（输入识别、输入形状、优先级全部透传），
     * 值先转成 Y 再经 {@code converter} 变成 X。
     *
     * <p>来源适配器在首次转换时惰性解析，注册顺序无关。alias 链允许，环不允许。
     *
     * @throws IllegalArgumentException 当 {@code target == from}
     */
    default <F, T> void registerAlias(Class<T> target, Class<F> from, Function<F, T> converter) {
        register(new AliasJSTypeAdapter<>(target, from, converter, null, this));
    }

    /** 同 {@link #registerAlias(Class, Class, Function)}，但显式指定优先级（默认沿用来源适配器）。 */
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
