package com.tkisor.nekojs.api.data;

import java.util.Objects;

public final class JsNumber extends Number {
    private final double value;
    private final String canonicalText;

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

    public String canonicalText() {
        return canonicalText;
    }

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

    @Override
    public String toString() {
        return canonicalText;
    }
}
