package com.tkisor.nekojs.api.data;

import com.tkisor.nekojs.api.ScriptType;

import java.util.Objects;

/// @author ZZZank
public interface Binding {
    static Binding of(String name, Object value) {
        return new SimpleBinding(name, value);
    }

    /// Sets [valueType] explicitly for cases where Java reflection cannot see the script-visible
    /// members of value's own class. The typical case is a [graal.graalvm.polyglot.proxy.ProxyObject]
    /// proxy delegate: reflection cannot reach its dynamic members, so preflight
    /// ([com.tkisor.nekojs.core.compiler.GlobalBindingMemberValidator]) wrongly reports
    /// "has no member". Point valueType at the class that actually holds those members.
    static Binding of(String name, Object value, Class<?> valueType) {
        return new TypedBinding(name, value, valueType);
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

    /// Same as [SimpleBinding], but carries [valueType] explicitly and overrides the interface
    /// default of `value.getClass()`.
    record TypedBinding(String name, Object value, Class<?> valueType) implements Binding {
        public TypedBinding {
            Objects.requireNonNull(name, "name == null");
            Objects.requireNonNull(valueType, "valueType == null");
        }
    }
}
