package com.tkisor.nekojs.core.bridge;

import graal.graalvm.polyglot.Value;

/**
 * Normalizes script numbers to narrow Java primitives at script-facing parameter
 * seams (the defect class of D6 and the 2026-09-29 float-coercion hotfix).
 *
 * <p>Script numbers are doubles. {@link Value#asFloat()} additionally refuses any
 * double that cannot round-trip through {@code float} exactly, and
 * {@link Value#asInt()}/{@link Value#asLong()} refuse fractional values, so
 * perfectly valid script values such as {@code 0.9} for a float-typed property
 * throw a raw engine {@code PolyglotException} ("Invalid or lossy primitive
 * coercion") at the seam — and at a reload collection point that exception fails
 * the whole reload phase instead of the offending call.
 *
 * <p>Following the D6 precedent (script-facing numeric seams normalize
 * explicitly), these methods read the number once as a double and narrow it with
 * an explicit policy:
 *
 * <ul>
 *   <li>{@code toFloat}: any finite number narrows with Java casting semantics;
 *       magnitudes beyond the float range are rejected instead of silently
 *       collapsing to infinity;</li>
 *   <li>{@code toInt}/{@code toLong}: integral numbers within the target range
 *       narrow exactly; fractional or out-of-range numbers are rejected.</li>
 * </ul>
 *
 * <p>All rejections are English {@link IllegalArgumentException}s carrying the
 * caller-supplied member context, so a bad value fails the offending call with a
 * legible message instead of surfacing an engine coercion error.
 */
public final class ScriptNumberCoercion {

    private ScriptNumberCoercion() {}

    /** Narrows a script number to an {@code int} parameter (integral, in range). */
    public static int toInt(Value value, String what) {
        double raw = requireFiniteNumber(value, what);
        if (raw != Math.rint(raw) || raw < Integer.MIN_VALUE || raw > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(what + " expects an integer in the int range, got " + raw);
        }
        return (int) raw;
    }

    /** Narrows a script number to a {@code long} parameter (integral, in range). */
    public static long toLong(Value value, String what) {
        double raw = requireFiniteNumber(value, what);
        if (raw != Math.rint(raw) || raw < Long.MIN_VALUE || raw >= 0x1.0p63) {
            throw new IllegalArgumentException(what + " expects an integer in the long range, got " + raw);
        }
        return (long) raw;
    }

    /** Narrows a script number to a {@code float} parameter (finite, Java casting semantics). */
    public static float toFloat(Value value, String what) {
        double raw = requireFiniteNumber(value, what);
        float narrowed = (float) raw;
        if (Float.isInfinite(narrowed)) {
            throw new IllegalArgumentException(what + " expects a number in the float range, got " + raw);
        }
        return narrowed;
    }

    private static double requireFiniteNumber(Value value, String what) {
        if (!value.isNumber()) {
            throw new IllegalArgumentException(what + " expects a number");
        }
        double raw = value.asDouble();
        if (!Double.isFinite(raw)) {
            throw new IllegalArgumentException(what + " expects a finite number, got " + raw);
        }
        return raw;
    }
}
