package com.tkisor.nekojs.core.pack;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/** Numeric versions with SemVer prerelease ordering and comparator conjunctions. */
public record PackVersion(int major, int minor, int patch, List<String> prerelease) implements Comparable<PackVersion> {
    static final String NUMERIC_PATTERN = "(?:0|[1-9][0-9]{0,8})";
    static final String PRERELEASE_PATTERN = "(?:0|[1-9][0-9]*|[0-9]*[A-Za-z-][0-9A-Za-z-]*)";
    static String pattern(boolean threeParts) {
        return NUMERIC_PATTERN + (threeParts ? "\\." + NUMERIC_PATTERN + "\\." + NUMERIC_PATTERN
                : "(?:\\." + NUMERIC_PATTERN + "){0,2}")
                + "(?:-" + PRERELEASE_PATTERN + "(?:\\." + PRERELEASE_PATTERN + ")*)?"
                + "(?:\\+[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?";
    }
    private static final Pattern VERSION = Pattern.compile(
            "(0|[1-9][0-9]{0,8})(?:\\.(0|[1-9][0-9]{0,8}))?(?:\\.(0|[1-9][0-9]{0,8}))?"
                    + "(?:-([0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*))?(?:\\+[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?");

    public PackVersion { prerelease = List.copyOf(prerelease); }

    public static PackVersion parse(String text) {
        var match = VERSION.matcher(text == null ? "" : text);
        if (!match.matches()) throw invalid("version", text);
        List<String> prerelease = match.group(4) == null ? List.of() : Arrays.asList(match.group(4).split("\\."));
        for (String identifier : prerelease) {
            if (identifier.matches("[0-9]+") && identifier.length() > 1 && identifier.startsWith("0")) {
                throw invalid("prerelease", text);
            }
        }
        return new PackVersion(Integer.parseInt(match.group(1)),
                match.group(2) == null ? 0 : Integer.parseInt(match.group(2)),
                match.group(3) == null ? 0 : Integer.parseInt(match.group(3)), prerelease);
    }

    public static void validateRange(String range) {
        if ("*".equals(range)) return;
        if (range == null || range.isBlank() || !range.equals(range.trim())) throw invalid("range", range);
        for (String term : range.split(" +")) {
            String version = term.replaceFirst("^(>=|<=|>|<|=)", "");
            parse(version);
        }
    }

    public static boolean satisfies(String version, String range) {
        validateRange(range);
        if ("*".equals(range)) return true;
        PackVersion actual;
        try { actual = parse(version); } catch (IllegalArgumentException invalid) { return false; }
        for (String term : range.split(" +")) {
            String operator = term.startsWith(">=") || term.startsWith("<=") ? term.substring(0, 2)
                    : term.startsWith(">") || term.startsWith("<") || term.startsWith("=") ? term.substring(0, 1) : "";
            int order = actual.compareTo(parse(term.substring(operator.length())));
            boolean accepted = switch (operator) {
                case ">=" -> order >= 0;
                case "<=" -> order <= 0;
                case ">" -> order > 0;
                case "<" -> order < 0;
                default -> order == 0;
            };
            if (!accepted) return false;
        }
        return true;
    }

    @Override
    public int compareTo(PackVersion other) {
        int order = Integer.compare(major, other.major);
        if (order == 0) order = Integer.compare(minor, other.minor);
        if (order == 0) order = Integer.compare(patch, other.patch);
        if (order != 0) return order;
        if (prerelease.isEmpty()) return other.prerelease.isEmpty() ? 0 : 1;
        if (other.prerelease.isEmpty()) return -1;
        for (int index = 0; index < Math.min(prerelease.size(), other.prerelease.size()); index++) {
            String left = prerelease.get(index), right = other.prerelease.get(index);
            boolean leftNumeric = left.matches("[0-9]+"), rightNumeric = right.matches("[0-9]+");
            if (leftNumeric && rightNumeric) {
                order = Integer.compare(left.length(), right.length());
                if (order == 0) order = left.compareTo(right);
            } else if (leftNumeric != rightNumeric) order = leftNumeric ? -1 : 1;
            else order = left.compareTo(right);
            if (order != 0) return order;
        }
        return Integer.compare(prerelease.size(), other.prerelease.size());
    }

    private static IllegalArgumentException invalid(String field, String value) {
        return new IllegalArgumentException("[NEKO-1016] Invalid pack " + field + ": " + value);
    }
}
