package com.tkisor.nekojs.api.data;

import com.tkisor.nekojs.script.ScriptType;

import java.util.Objects;

/// @author ZZZank
public interface Binding {
    static Binding of(String name, Object value) {
        return new SimpleBinding(name, value);
    }

    String name();

    Object value();

    default Class<?> valueType() {
        var value = value();
        return value == null ? Void.class : value instanceof Class<?> c ? c : value.getClass();
    }

    /// Invoked when reloading script
    default void close(ScriptType scriptType) {
    }

    record SimpleBinding(String name, Object value) implements Binding {
        public SimpleBinding {
            Objects.requireNonNull(name, "name == null");
        }
    }
}
