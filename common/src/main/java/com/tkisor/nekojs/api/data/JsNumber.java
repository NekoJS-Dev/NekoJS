package com.tkisor.nekojs.api.data;

import java.util.Objects;

/**
 * JS numeric value that also carries its original text, preserving the literal form the
 * script wrote.
 *
 * <p>Used to reproduce the exact text of a JS number in JSON, NBT and similar output (for
 * example {@code "1e21"} or {@code "0.1"}), while still offering the standard {@link Number}
 * accessors. The value must be finite. Immutable.
 */
public final class JsNumber extends Number {
    private static final long serialVersionUID = 1L;

    private final double value;
    private final String canonicalText;

    /**
     * @param value         the numeric value; must be finite (not NaN or infinite)
     * @param canonicalText the canonical text form of the value; must not be blank
     */
    public JsNumber(double value, String canonicalText) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("value must be finite");
        }
        this.value = value;
        this.canonicalText = Objects.requireNonNull(canonicalText, "canonicalText");
        if (canonicalText.isBlank()) {
            throw new IllegalArgumentException("canonicalText must not be blank");
        }
    }

    /** Returns the canonical text form. */
    public String canonicalText() {
        return canonicalText;
    }

    /** Returns a {@link Long} when the value is an integer that fits in a long, otherwise a {@link Double}. */
    public Number nativeNumber() {
        if (value == Math.rint(value) && value >= -0x1.0p63 && value < 0x1.0p63) {
            return Long.valueOf((long) value);
        }
        return Double.valueOf(value);
    }

    @Override public int intValue() { return (int) value; }
    @Override public long longValue() { return (long) value; }
    @Override public float floatValue() { return (float) value; }
    @Override public double doubleValue() { return value; }

    /** Returns the canonical text form, so the number prints the way the script wrote it. */
    @Override
    public String toString() {
        return canonicalText;
    }
}
