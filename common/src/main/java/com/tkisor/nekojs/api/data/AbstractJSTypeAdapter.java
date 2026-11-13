package com.tkisor.nekojs.api.data;

import com.tkisor.nekojs.api.JSTypeAdapter;
import graal.graalvm.polyglot.Value;

/**
 * Base class for the template-style type adapters. It fixes the shared dispatch template for
 * {@link #test(Value)} and {@link #apply(Value)}:
 *
 * <pre>{@code
 *   null      -> acceptNull() ? defaultValue() : throw
 *   string    -> fromString(s)
 *   hostObj   -> fromHostObject(obj)   (returning null means "not recognised")
 *   other     -> acceptOther(v) ? fromOther(v) : throw
 * }</pre>
 *
 * <p>Subclasses override only the hooks they need (usually {@link #fromString},
 * {@link #fromHostObject} and {@link #defaultValue}) instead of repeating the
 * null/instanceof/throw boilerplate.
 *
 * <p><b>No Minecraft dependency</b>: {@link #fromHostObject(Object)} takes an {@code Object}
 * (from {@link Value#asHostObject()}), and the subclass runs the instanceof checks on the
 * platform side. This base class imports no Minecraft class, so it can stay in the common
 * module.
 *
 * <p><b>test/apply agreement</b>: a {@code null} return from
 * {@link #fromHostObject(Object)} means "this host object is not recognised". {@code test}
 * then returns {@code false}, so GraalJS never forwards that value, and {@code apply} throws
 * {@link ValueConversionException}. The two can no longer disagree, with test passing while
 * apply fails. <b>Never use {@code null} as a legal conversion result</b>: an adapter that
 * returns null causes a downstream NPE. To express an empty value, use the empty constant of
 * the type ({@code ItemStack.EMPTY}, {@code Items.AIR}).
 */
public abstract class AbstractJSTypeAdapter<T> implements JSTypeAdapter<T> {

    // ===================== Configurable hooks (subclasses override as needed) =====================

    /**
     * Returns the value used for null or empty input. The default {@code null} means
     * <b>null input is not accepted</b>, together with the default {@link #acceptNull()}.
     * A subclass that accepts null (Item/Block return AIR, for example) overrides this method
     * and returns a non-null empty constant.
     */
    protected T defaultValue() {
        return null;
    }

    /**
     * Returns whether null input is accepted. By default this follows whether
     * {@link #defaultValue()} is non-null. A subclass may override it explicitly, for example
     * when it accepts null but its default value stays null.
     */
    protected boolean acceptNull() {
        return defaultValue() != null;
    }

    /**
     * Converts from a string. The default throws {@link ValueConversionException}, meaning this
     * adapter does not accept string input; subclasses that accept strings override this
     * method.
     */
    protected T fromString(String s) {
        throw new ValueConversionException(getTargetClass(), "string", s,
            "adapter does not accept string input");
    }

    /**
     * Converts from a host object. Subclasses run an instanceof chain:
     * <pre>{@code
     * if (host instanceof Foo f) return f;
     * if (host instanceof Bar b) return convert(b);
     * return null;   // not recognised
     * }</pre>
     * Returning {@code null} means "this host object is not recognised"; never use it as a
     * legal result. If the object is recognised but its data is invalid, throw
     * {@link ValueConversionException}.
     */
    protected abstract T fromHostObject(Object host);

    /**
     * Returns whether other shapes are accepted (JS objects with {@code hasMembers}, arrays
     * with {@code hasArrayElements}, and so on). The default is {@code false}; subclasses that
     * take objects or arrays override this method.
     */
    protected boolean acceptOther(Value value) {
        return false;
    }

    /**
     * Converts input for which {@link #acceptOther(Value)} returned {@code true}. The default
     * throws.
     */
    protected T fromOther(Value value) {
        throw new ValueConversionException(getTargetClass(), "value", value,
            "adapter does not accept this input shape");
    }

    // ===================== Template methods (final, subclasses cannot override) =====================

    @Override
    public final boolean test(Value value) {
        if (value == null || value.isNull()) return acceptNull();
        if (value.isString()) {
            // Probe fromString: the default implementation throws ValueConversionException, so the
            // string shape is not accepted. test and apply then agree instead of test passing
            // while apply throws.
            try {
                fromString(value.asString());
                return true;
            } catch (ValueConversionException e) {
                return false;
            }
        }
        if (value.isHostObject()) {
            try {
                return fromHostObject(value.asHostObject()) != null;
            } catch (ValueConversionException e) {
                // The host object was not recognised, so the value is not accepted
                return false;
            }
        }
        return acceptOther(value);
    }

    @Override
    public final T apply(Value value) {
        if (value == null || value.isNull()) {
            if (!acceptNull()) {
                throw new ValueConversionException(getTargetClass(), "non-null value", value,
                    "null input is not accepted by this adapter");
            }
            return defaultValue();
        }
        if (value.isString()) {
            return fromString(value.asString());
        }
        if (value.isHostObject()) {
            T r = fromHostObject(value.asHostObject());
            if (r == null) {
                throw new ValueConversionException(getTargetClass(), "recognized host object", value,
                    "unrecognized host object type for this adapter");
            }
            return r;
        }
        if (acceptOther(value)) {
            return fromOther(value);
        }
        throw new ValueConversionException(getTargetClass(), "string | recognized host object | supported shape", value,
            "unsupported input shape");
    }
}
