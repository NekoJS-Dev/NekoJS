package dev.latvian.mods.rhino;

import dev.latvian.mods.rhino.type.TypeInfo;
import graal.graalvm.polyglot.Value;

/** Stateless conversion facade backed by the caller's Graal context, never an evaluator. */
public class Context {
    public Object jsToJava(Object value, TypeInfo target) {
        value = Wrapper.unwrapped(value);
        if (value == null || target.asClass().isInstance(value)) return value;
        if (value instanceof Value nativeValue) return nativeValue.as(target.asClass());
        try { return Value.asValue(value).as(target.asClass()); }
        catch (RuntimeException failure) {
            throw new IllegalArgumentException("[NEKO-4261] Legacy value conversion failed: " + target.asClass().getName(), failure);
        }
    }
    public Object jsToJava(Object value, Class<?> target) { return jsToJava(value, TypeInfo.of(target)); }
}
