package com.tkisor.nekojs.client.gui.dashboard;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;
import java.util.regex.Pattern;

/** Visual wrapping only: callers always copy the original DTO string. */
public final class DashboardText {
    private static final Pattern STACK = Pattern.compile("(?m)^[\\t ]+at[\\t ]+");
    private DashboardText() {}
    public record Parts(String body, String stack) {}
    public static Parts splitStack(String raw) {
        var match = STACK.matcher(raw);
        return match.find() ? new Parts(raw.substring(0, match.start()), raw.substring(match.start()))
                : new Parts(raw, "");
    }
    public static String display(String raw) {
        return raw.replace("\r\n", "\n").replace('\r', '\n').replace("\t", "    ")
                .replace('§', '�').replace('\0', '�');
    }
    public static String fit(String text, int width, ToIntFunction<String> measure) {
        String s = display(text).replace('\n', ' ');
        if (width <= 0) return "";
        if (measure.applyAsInt(s) <= width) return s;
        String suffix = "…";
        int room = width - measure.applyAsInt(suffix);
        if (room < 0) return "";
        return s.substring(0, fittingEnd(s, 0, room, measure)) + suffix;
    }
    public static List<String> wrap(String raw, int width, ToIntFunction<String> measure) {
        return wrap(raw, width, Integer.MAX_VALUE, measure);
    }
    private static List<String> wrap(String raw, int width, int limit, ToIntFunction<String> measure) {
        List<String> lines = new ArrayList<>();
        int available = Math.max(1, width);
        for (String line : display(raw).split("\n", -1)) {
            if (lines.size() >= limit) break;
            if (line.isEmpty()) { lines.add(""); continue; }
            int start = 0;
            while (start < line.length() && lines.size() < limit) {
                int end = fittingEnd(line, start, available, measure);
                if (end == start) end = start + Character.charCount(line.codePointAt(start));
                lines.add(line.substring(start, end));
                start = end;
            }
        }
        return lines;
    }
    public static List<String> preview(String raw, int width, int limit, ToIntFunction<String> measure) {
        List<String> lines = wrap(raw, width, limit + 1, measure);
        if (lines.size() <= limit) return lines;
        List<String> result = new ArrayList<>(lines.subList(0, limit));
        result.set(limit - 1, fit(result.get(limit - 1) + "…", width, measure));
        return result;
    }
    private static int fittingEnd(String s, int start, int width, ToIntFunction<String> measure) {
        // Exponential local search: never scan the entire remaining 262 KiB line for every visual row.
        int fitted = start, step = 1;
        while (fitted < s.length()) {
            int end = fitted, count = 0;
            while (end < s.length() && count < step) {
                end += Character.charCount(s.codePointAt(end)); count++;
            }
            if (measure.applyAsInt(s.substring(start, end)) <= width) {
                fitted = end; step = Math.min(1 << 20, step * 2); continue;
            }
            int low = 0, high = count;
            while (low < high) {
                int mid = (low + high + 1) / 2;
                int candidate = s.offsetByCodePoints(fitted, mid);
                if (measure.applyAsInt(s.substring(start, candidate)) <= width) low = mid;
                else high = mid - 1;
            }
            return s.offsetByCodePoints(fitted, low);
        }
        return fitted;
    }
}
