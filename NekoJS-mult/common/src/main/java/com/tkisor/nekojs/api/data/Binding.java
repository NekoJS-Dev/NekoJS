package com.tkisor.nekojs.api.data;

import com.tkisor.nekojs.api.ScriptType;

import java.util.Objects;

/// @author ZZZank
public interface Binding {
    static Binding of(String name, Object value) {
        return new SimpleBinding(name, value);
    }

    /// 显式指定 [valueType]，用于 value 本身的 class 无法被 Java 反射出脚本可见成员的情况——
    /// 典型是 [graal.graalvm.polyglot.proxy.ProxyObject] 代理委托：动态成员 Java 反射不到，
    /// 会让 preflight（[com.tkisor.nekojs.core.compiler.GlobalBindingMemberValidator]）误报
    /// "has no member"。此时把 valueType 指向真正承载这些成员的类即可。
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

    /// 与 [SimpleBinding] 相同，但显式携带 [valueType]（覆盖接口默认的 `value.getClass()`）。
    record TypedBinding(String name, Object value, Class<?> valueType) implements Binding {
        public TypedBinding {
            Objects.requireNonNull(name, "name == null");
            Objects.requireNonNull(valueType, "valueType == null");
        }
    }
}
