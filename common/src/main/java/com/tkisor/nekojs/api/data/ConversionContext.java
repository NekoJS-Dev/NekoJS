package com.tkisor.nekojs.api.data;

/**
 * Context object for type conversion; carries optional extra information through an adapter
 * conversion.
 *
 * <p>It is currently an empty placeholder, reserved for future fields such as the conversion
 * source or error location. Get the shared empty instance with {@link #empty()}. Immutable and
 * thread-safe.
 */
public final class ConversionContext {
    private static final ConversionContext EMPTY = new ConversionContext();

    private ConversionContext() {}

    /** Returns the shared empty context instance. */
    public static ConversionContext empty() {
        return EMPTY;
    }
}
