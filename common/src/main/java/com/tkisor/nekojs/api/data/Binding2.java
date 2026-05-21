package com.tkisor.nekojs.api.data;

import java.util.Objects;

/// @author ZZZank
public interface Binding2 {
    static Binding2 of(String name, Object value) {
        return new SimpleBinding(name, value);
    }

    String name();

    Object value();

    default Class<?> valueType() {
        var value = value();
        return value == null ? Void.class : value instanceof Class<?> c ? c : value.getClass();
    }

    /// Invoked when reloading script, and old binding is being removed
    default void close() {
    }

    record SimpleBinding(String name, Object value) implements Binding2 {
        public SimpleBinding {
            Objects.requireNonNull(name, "name == null");
        }
    }
}
