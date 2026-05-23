package com.tkisor.nekojs.api.data;

import com.tkisor.nekojs.api.JSTypeAdapter;
import graal.graalvm.polyglot.Value;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * JS类型适配器注册接口
 * <p>
 * 用于注册自定义的JavaScript类型适配器，实现Java类型与JavaScript类型之间的转换
 * </p>
 * 
 * @author tkisor
 * @since 1.0
 */
@FunctionalInterface
public interface JSTypeAdapterRegister {
    /**
     * 注册JS类型适配器
     * 
     * @param <T> 要适配的Java类型
     * @param adapter JS类型适配器实例
     */
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
            public boolean canConvert(Value value) {
                return filter.test(value);
            }

            @Override
            public T convert(Value value) {
                return converter.apply(value);
            }
        }

        register(new LambdaJSTypeAdapter<>(target, filter, converter));
    }
}