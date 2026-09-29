package com.tkisor.nekojs.api.ui;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Immutable UI color as an ARGB int. Parsed from script values with a controlled
 * grammar — ARGB/RGB integers, {@code #RGB}, {@code #RRGGBB}, {@code #AARRGGBB} hex
 * strings, and the CSS basic named colors — so arbitrary CSS color functions never
 * enter the UI contract.
 */
public final class UiColor {
    /** CSS basic keyword colors (plus {@code transparent}) with their opaque ARGB values. */
    private static final Map<String, Integer> NAMED = Map.ofEntries(
            Map.entry("transparent", 0x00000000),
            Map.entry("black", 0xFF000000),
            Map.entry("silver", 0xFFC0C0C0),
            Map.entry("gray", 0xFF808080),
            Map.entry("white", 0xFFFFFFFF),
            Map.entry("maroon", 0xFF800000),
            Map.entry("red", 0xFFFF0000),
            Map.entry("purple", 0xFF800080),
            Map.entry("fuchsia", 0xFFFF00FF),
            Map.entry("green", 0xFF008000),
            Map.entry("lime", 0xFF00FF00),
            Map.entry("olive", 0xFF808000),
            Map.entry("yellow", 0xFFFFFF00),
            Map.entry("navy", 0xFF000080),
            Map.entry("blue", 0xFF0000FF),
            Map.entry("teal", 0xFF008080),
            Map.entry("aqua", 0xFF00FFFF));

    private final int argb;

    private UiColor(int argb) {
        this.argb = argb;
    }

    /** The color as an ARGB int, matching the platform painter color convention. */
    public int argb() {
        return argb;
    }

    /**
     * Parses a script color value: an integral number in the int32/uint32 range read
     * as ARGB bits (script bitwise color math yields negative int32s), or a
     * hex/named color string.
     *
     * @param value raw prop value
     * @return the color, or empty when the value is not a controlled color form
     */
    public static Optional<UiColor> parse(Object value) {
        if (value instanceof Number number) {
            double raw = number.doubleValue();
            if (!fitsArgbNumberRange(raw)) return Optional.empty();
            return Optional.of(new UiColor(argbBits(raw)));
        }
        if (value instanceof String string) return parseString(string);
        return Optional.empty();
    }

    /**
     * Coerces a script color number to ARGB int bits for painter-style seams
     * (defect D6): an integral value in the int32/uint32 range is read as ARGB
     * bits. Script hex literals such as {@code 0xFFFFFF00} are unsigned values
     * above {@code Integer.MAX_VALUE}; declaring the parameter as a Java
     * {@code int} saturates them to {@code 0x7FFFFFFF} (translucent white) at the
     * engine conversion boundary, so script-facing color parameters must accept
     * {@link Number} and normalize through this method.
     *
     * @param value the script-supplied color number ({@code null} from JS {@code null}/{@code undefined})
     * @return the ARGB bits (unsigned literals wrap to negative int32s)
     * @throws IllegalArgumentException when the value is null, fractional or outside
     *         the int32/uint32 color range
     */
    public static int argbBits(Number value) {
        if (value == null) {
            throw new IllegalArgumentException("color must not be null");
        }
        double raw = value.doubleValue();
        if (!fitsArgbNumberRange(raw)) {
            throw new IllegalArgumentException(
                    "color must be an integral number in the int32/uint32 ARGB range: " + raw);
        }
        return argbBits(raw);
    }

    /** Reads an already-range-checked integral double as ARGB bits (low 32 bits). */
    private static int argbBits(double raw) {
        return (int) (long) raw;
    }

    /** Shared numeric rule: integral and inside [int32 min, uint32 max]. */
    private static boolean fitsArgbNumberRange(double raw) {
        return raw == Math.floor(raw) && raw >= Integer.MIN_VALUE && raw <= 0xFFFFFFFFL;
    }

    /** Parses {@code #RGB}, {@code #RRGGBB}, {@code #AARRGGBB} (case-insensitive) or a named color. */
    public static Optional<UiColor> parseString(String value) {
        if (value == null) return Optional.empty();
        String text = value.trim().toLowerCase(Locale.ROOT);
        Integer named = NAMED.get(text);
        if (named != null) return Optional.of(new UiColor(named));
        if (text.length() == 4 || text.length() == 7 || text.length() == 9) {
            if (text.charAt(0) != '#') return Optional.empty();
            String hex = text.substring(1);
            if (!hex.chars().allMatch(c -> Character.digit(c, 16) >= 0)) return Optional.empty();
            if (hex.length() == 3) {
                // CSS short form expands each digit: #F00 is #FF0000, not #000F00.
                StringBuilder expanded = new StringBuilder(6);
                for (char c : hex.toCharArray()) expanded.append(c).append(c);
                hex = expanded.toString();
            }
            return Optional.of(new UiColor((int) Long.parseLong(hex, 16) | alphaFill(hex.length())));
        }
        return Optional.empty();
    }

    /** Expands 3/6 digit hex to full ARGB; 8 digit hex already carries alpha. */
    private static int alphaFill(int digits) {
        return switch (digits) {
            case 3, 6 -> 0xFF000000;
            default -> 0;
        };
    }
}
