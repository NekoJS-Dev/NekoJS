package com.tkisor.nekojs.bindings.static_access;

public final class TimeJS {
    public final int SECOND = 20;
    public final int MINUTE = SECOND * 60;
    public final int HOUR = MINUTE * 60;

    public int seconds(int value) {
        return value * SECOND;
    }

    public int minutes(int value) {
        return value * MINUTE;
    }

    public int hours(int value) {
        return value * HOUR;
    }

    /**
     * 解析时间字符串为 tick 数（1 秒 = 20 tick）。支持单位后缀：
     * {@code ms}(毫秒，1tick=50ms，向下取整)、{@code s}(秒)、{@code m}(分)、{@code h}(小时)、{@code t}(tick)；
     * 纯数字或未知单位按 tick 处理。
     * <pre>
     * Time.parseTime("5s")   // 100
     * Time.parseTime("10m")  // 12000
     * Time.parseTime("2h")   // 144000
     * Time.parseTime("100t") // 100
     * </pre>
     */
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
     * 解析时间字符串为毫秒数（{@code "5s"→5000}、{@code "2t"→100}、{@code "100ms"→100}）。纯数字按毫秒。
     */
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
