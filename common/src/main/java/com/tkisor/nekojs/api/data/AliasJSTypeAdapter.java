package com.tkisor.nekojs.api.data;

import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.JSTypeAdapter;

import graal.graalvm.polyglot.Value;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Alias adapter: target type X reuses the adapter of source type Y for input recognition. The
 * value is converted to Y first and then goes through {@code converter: Y -> X} to produce the
 * final value; this is the counterpart of KubeJS {@code registerAlias}.
 *
 * <p>The source adapter is resolved lazily from the registry on first use, by
 * {@code getTargetClass() == from}, and then cached, so an alias may be registered before its
 * source adapter. Alias chains (an alias pointing at another alias) work through plain
 * delegation: the X->Y alias calls Y's adapter, and if Y is itself an alias it delegates
 * again, so resolution takes one step. A cycle (X->Y->X) is detected on first use by the
 * active set kept during conversion and throws {@link IllegalStateException}:
 * {@link #test(Value)} treats it as "not accepted", while {@link #apply(Value)} rethrows it so
 * a configuration error surfaces early.
 *
 * <p>The probe side ({@link #inputShapes()}/{@link #syntaxDoc()}) passes the source adapter's
 * declarations through unchanged, and the alias generator renders them under X's own name
 * {@code $X_} (across packages a union type must be self-contained, so no
 * {@code $X_ = $Y_} reference is emitted). A conversion failure is reported by the source
 * adapter, whose target is Y, because the failure happened in the Y step and that is more
 * accurate for debugging.
 */
public final class AliasJSTypeAdapter<F, T> implements JSTypeAdapter<T> {

    /** Active set for the current conversion: re-entering the same alias within one conversion means a cycle. */
    private static final ThreadLocal<Set<AliasJSTypeAdapter<?, ?>>> ACTIVE =
            ThreadLocal.withInitial(HashSet::new);

    private final Class<T> target;
    private final Class<F> from;
    private final Function<F, T> converter;
    private final ConversionPrecedence precedenceOverride;
    private final JSTypeAdapterRegistry registry;
    private JSTypeAdapter<F> resolved;

    public AliasJSTypeAdapter(Class<T> target, Class<F> from, Function<F, T> converter,
                              ConversionPrecedence precedenceOverride, JSTypeAdapterRegistry registry) {
        this.target = Objects.requireNonNull(target, "target");
        this.from = Objects.requireNonNull(from, "from");
        this.converter = Objects.requireNonNull(converter, "converter");
        this.precedenceOverride = precedenceOverride;
        this.registry = Objects.requireNonNull(registry, "registry");
        if (target == from) {
            throw new IllegalArgumentException("alias target must differ from source: " + target.getName());
        }
    }

    @Override
    public Class<T> getTargetClass() {
        return target;
    }

    @Override
    public ConversionPrecedence getPrecedence() {
        // Registration (building hostAccess) can happen before the source adapter is visible; when it
        // cannot be resolved, fall back to LOWEST
        if (precedenceOverride != null) return precedenceOverride;
        JSTypeAdapter<F> source = peekResolved();
        return source != null ? source.getPrecedence() : ConversionPrecedence.LOWEST;
    }

    @Override
    public List<AdapterInputShape> inputShapes() {
        JSTypeAdapter<F> source = peekResolved();
        return source != null ? source.inputShapes() : List.of();
    }

    @Override
    public Optional<String> syntaxDoc() {
        JSTypeAdapter<F> source = peekResolved();
        return source != null ? source.syntaxDoc() : Optional.empty();
    }

    @Override
    public boolean test(Value value) {
        Set<AliasJSTypeAdapter<?, ?>> active = ACTIVE.get();
        if (!active.add(this)) return false; // A cycle is treated as "not accepted"; apply reports the error explicitly
        try {
            return resolve().test(value);
        } catch (IllegalStateException unresolvable) {
            return false;
        } finally {
            active.remove(this);
        }
    }

    @Override
    public T apply(Value value) {
        Set<AliasJSTypeAdapter<?, ?>> active = ACTIVE.get();
        if (!active.add(this)) {
            throw new IllegalStateException("alias cycle while converting to " + target.getName());
        }
        try {
            return converter.apply(resolve().apply(value));
        } finally {
            active.remove(this);
        }
    }

    /**
     * Resolves read-only: does not cache the result and returns null when the source cannot be
     * resolved. Used by best-effort reads such as probe and precedence queries.
     */
    private JSTypeAdapter<F> peekResolved() {
        try {
            return resolved != null ? resolved : find();
        } catch (IllegalStateException unresolvable) {
            return null;
        }
    }

    private JSTypeAdapter<F> resolve() {
        if (resolved != null) return resolved;
        JSTypeAdapter<F> source = find();
        resolved = source;
        return source;
    }

    @SuppressWarnings("unchecked")
    private JSTypeAdapter<F> find() {
        for (JSTypeAdapter<?> candidate : registry.view()) {
            if (candidate.getTargetClass() == from) {
                return (JSTypeAdapter<F>) candidate;
            }
        }
        throw new IllegalStateException("no adapter registered for alias source " + from.getName()
                + " (target " + target.getName() + ")");
    }
}
