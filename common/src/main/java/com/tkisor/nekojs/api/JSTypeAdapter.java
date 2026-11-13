package com.tkisor.nekojs.api;

import com.tkisor.nekojs.api.data.ConversionPrecedence;

import graal.graalvm.polyglot.Value;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * JS type adapter interface.<p>
 * A plugin class can implement this interface to provide custom type conversions.
 */
public interface JSTypeAdapter<T> extends Predicate<Value>, Function<Value, T> {
    Class<T> getTargetClass();

    default ConversionPrecedence getPrecedence() {
        return ConversionPrecedence.LOWEST;
    }

    /**
     * Declares the input shapes this adapter accepts, so that probe can generate a permissive
     * TypeScript input alias.
     *
     * <p>An empty list (the default) means nothing is declared: probe generates no {@code $Foo_}
     * alias for that target and does not widen parameters that reference the type. Implementing
     * this method is recommended, so that the range accepted by {@link #test(Value)} matches the
     * generated type declarations.
     *
     * @see AdapterInputShape
     */
    default List<AdapterInputShape> inputShapes() {
        return List.of();
    }

    /**
     * Declares the input syntax this adapter accepts, so that probe renders it as a JSDoc comment
     * above the TypeScript input alias ({@code $Foo_}) and IDE users can see the available string
     * and object syntax.
     *
     * <p>For example, the ingredient adapter can document the {@code #tag} / {@code @mod} /
     * {@code *} / {@code /regex/} prefixes and the {@code { mod?, filter?, ... }} object form here.
     * Empty by default (no documentation is attached).
     */
    default Optional<String> syntaxDoc() {
        return Optional.empty();
    }

    /**
     * Returns whether a JS value can be converted to the target type.
     */
    @Override
    boolean test(Value value);

    /**
     * Converts a JS value to the target type. When the conversion is not possible, throwing an
     * exception is safer than returning null.
     */
    @Override
    T apply(Value value);
}
