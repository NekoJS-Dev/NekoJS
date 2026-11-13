package com.tkisor.nekojs.bindings.static_access;

import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.api.annotation.Return;

/**
 * Time unit helpers (1 second = 20 ticks) and parsing of time strings.
 */
@Doc("Time unit helpers in game ticks (1 second = 20 ticks).")
public final class TimeJS {
    /** Number of ticks in one second. */
    @Doc("Ticks per second (20).")
    public final int SECOND = 20;
    /** Number of ticks in one minute. */
    @Doc("Ticks per minute (1200).")
    public final int MINUTE = SECOND * 60;
    /** Number of ticks in one hour. */
    @Doc("Ticks per hour (72000).")
    public final int HOUR = MINUTE * 60;

    /** Converts seconds to ticks. */
    @Doc("Converts seconds to ticks.")
    @Param(name = "value", value = "amount of seconds")
    @Return("equivalent amount of ticks")
    public int seconds(int value) {
        return value * SECOND;
    }

    /** Converts minutes to ticks. */
    @Doc("Converts minutes to ticks.")
    @Param(name = "value", value = "amount of minutes")
    @Return("equivalent amount of ticks")
    public int minutes(int value) {
        return value * MINUTE;
    }

    /** Converts hours to ticks. */
    @Doc("Converts hours to ticks.")
    @Param(name = "value", value = "amount of hours")
    @Return("equivalent amount of ticks")
    public int hours(int value) {
        return value * HOUR;
    }

    /**
     * Parses a time string into ticks (1 second = 20 ticks). Supported unit suffixes:
     * {@code ms} (milliseconds, 1 tick = 50 ms, rounded down), {@code s} (seconds), {@code m}
     * (minutes), {@code h} (hours) and {@code t} (ticks); a plain number or an unknown suffix is
     * treated as ticks.
     * <pre>
     * Time.parseTime("5s")   // 100
     * Time.parseTime("10m")  // 12000
     * Time.parseTime("2h")   // 144000
     * Time.parseTime("100t") // 100
     * </pre>
     */
    @Doc("Parses a time string like '5s', '10m', '2h', '100ms' or '100t' into ticks.")
    @Param(name = "str", value = "time string with unit suffix (ms/s/m/h/t); plain numbers are treated as ticks")
    @Return("tick count; 0 for null or empty input")
    public long parseTime(String str) {
        if (str == null) return 0L;
        String s = str.trim();
        if (s.isEmpty()) return 0L;
        if (s.endsWith("ms")) {
            return Long.parseLong(s.substring(0, s.length() - 2)) / 50L;
        }
        char last = s.charAt(s.length() - 1);
        if (Character.isLetter(last)) {
            long num = Long.parseLong(s.substring(0, s.length() - 1));
            return switch (last) {
                case 't' -> num;
                case 's' -> num * SECOND;
                case 'm' -> num * MINUTE;
                case 'h' -> num * HOUR;
                default -> num;
            };
        }
        return Long.parseLong(s);
    }

    /**
     * Parses a time string into milliseconds ({@code "5s"→5000}, {@code "2t"→100},
     * {@code "100ms"→100}). A plain number is treated as milliseconds.
     */
    @Doc("Parses a time string into milliseconds; plain numbers are treated as milliseconds.")
    @Param(name = "str", value = "time string with unit suffix (ms/s/m/h/t)")
    @Return("millisecond count; 0 for null or empty input")
    public long parseMs(String str) {
        if (str == null) return 0L;
        String s = str.trim();
        if (s.isEmpty()) return 0L;
        if (s.endsWith("ms")) {
            return Long.parseLong(s.substring(0, s.length() - 2));
        }
        char last = s.charAt(s.length() - 1);
        if (Character.isLetter(last)) {
            long num = Long.parseLong(s.substring(0, s.length() - 1));
            return switch (last) {
                case 't' -> num * 50L;
                case 's' -> num * 1000L;
                case 'm' -> num * 60_000L;
                case 'h' -> num * 3_600_000L;
                default -> num;
            };
        }
        return Long.parseLong(s);
    }
}
