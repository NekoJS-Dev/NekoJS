package com.tkisor.nekojs.script;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.Function;

/// @author ZZZank
public final class ScriptTypedValue<T> {
    /// Create a new [ScriptTypedValue] that does not allow `null` input, and will not initialize value automatically
    public static <T> ScriptTypedValue<T> of() {
        return new ScriptTypedValue<>(new Object[LENGTH], null);
    }

    /// Create a new [ScriptTypedValue] that does not allow `null` input, and will initialize value automatically using provided `initializer`
    public static <T> ScriptTypedValue<T> of(Function<? super @NotNull ScriptType, ? extends T> initializer) {
        Objects.requireNonNull(initializer, "initializer == null");
        return new ScriptTypedValue<>(new Object[LENGTH], initializer);
    }

    /// Create a new [ScriptTypedValue] that allows `null` input, and will not initialize value automatically
    public static <T> ScriptTypedValue<T> ofNullable() {
        return new ScriptTypedValue<>(new Object[LENGTH + 1], null);
    }

    /// Create a new [ScriptTypedValue] that allows `null` input, and will initialize value automatically using provided `initializer`
    public static <T> ScriptTypedValue<T> ofNullable(Function<? super @Nullable ScriptType, ? extends T> initializer) {
        Objects.requireNonNull(initializer, "initializer == null");
        return new ScriptTypedValue<>(new Object[LENGTH + 1], initializer);
    }

    private static final int LENGTH = ScriptType.values().length;

    private final Object[] internal;
    private final Function<? super ScriptType, ? extends T> initializer;

    private ScriptTypedValue(Object[] internal, Function<? super ScriptType, ? extends T> initializer) {
        this.internal = internal;
        this.initializer = initializer;
    }

    @SuppressWarnings("unchecked")
    private T getCasted(ScriptType type) {
        return (T) internal[index(type)];
    }

    private static int index(ScriptType type) {
        return type == null ? LENGTH : type.ordinal();
    }

    public T set(ScriptType type, T value) {
        var old = getCasted(type);
        internal[index(type)] = value;
        return old;
    }

    public T at(ScriptType type) {
        var got = getCasted(type);
        if (got == null && initializer != null) {
            got = initializer.apply(type);
            internal[index(type)] = got;
        }
        return got;
    }

    public T atClient() {
        return at(ScriptType.CLIENT);
    }

    public T atServer() {
        return at(ScriptType.SERVER);
    }

    public T atStartup() {
        return at(ScriptType.STARTUP);
    }

    public boolean hasInitializer() {
        return initializer != null;
    }

    public boolean acceptsNull() {
        return internal.length > LENGTH;
    }

    @Override
    public String toString() {
        return "ScriptTypedValue" + Arrays.toString(internal);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ScriptTypedValue<?> that
               && Arrays.equals(internal, that.internal)
               && Objects.equals(initializer, that.initializer);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(internal);
    }
}
