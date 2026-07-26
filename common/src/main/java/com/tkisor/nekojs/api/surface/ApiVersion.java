package com.tkisor.nekojs.api.surface;

import java.util.Comparator;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record ApiVersion(int major, int minor, int patch, String prerelease, String build)
        implements Comparable<ApiVersion> {

    private static final Pattern SEMVER = Pattern.compile(
            "^(?<major>0|[1-9]\\d*)\\.(?<minor>0|[1-9]\\d*)\\.(?<patch>0|[1-9]\\d*)" +
            "(?:-(?<prerelease>[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*))?" +
            "(?:\\+(?<build>[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*))?$");

    public static ApiVersion parse(String raw) {
        Objects.requireNonNull(raw, "raw");
        Matcher m = SEMVER.matcher(raw);
        if (!m.matches()) throw new IllegalArgumentException("invalid semver: " + raw);
        return new ApiVersion(
                Integer.parseInt(m.group("major")),
                Integer.parseInt(m.group("minor")),
                Integer.parseInt(m.group("patch")),
                m.group("prerelease"),
                m.group("build"));
    }

    @Override
    public int compareTo(ApiVersion other) {
        int c = Integer.compare(this.major, other.major);
        if (c != 0) return c;
        c = Integer.compare(this.minor, other.minor);
        if (c != 0) return c;
        c = Integer.compare(this.patch, other.patch);
        if (c != 0) return c;
        // prerelease presence: no prerelease > has prerelease
        if (this.prerelease == null && other.prerelease != null) return 1;
        if (this.prerelease != null && other.prerelease == null) return -1;
        if (this.prerelease != null) {
            return comparePrerelease(this.prerelease, other.prerelease);
        }
        return 0;
    }

    private static int comparePrerelease(String a, String b) {
        String[] aParts = a.split("\\.");
        String[] bParts = b.split("\\.");
        int len = Math.min(aParts.length, bParts.length);
        for (int i = 0; i < len; i++) {
            boolean aNum = isNumeric(aParts[i]);
            boolean bNum = isNumeric(bParts[i]);
            if (aNum && bNum) {
                int c = Long.compare(Long.parseLong(aParts[i]), Long.parseLong(bParts[i]));
                if (c != 0) return c;
            } else if (aNum) {
                return -1; // numeric < alphanumeric
            } else if (bNum) {
                return 1;
            } else {
                int c = aParts[i].compareTo(bParts[i]);
                if (c != 0) return c;
            }
        }
        return Integer.compare(aParts.length, bParts.length);
    }

    private static boolean isNumeric(String s) {
        if (s.isEmpty()) return false;
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) return false;
        }
        return true;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(major).append('.').append(minor).append('.').append(patch);
        if (prerelease != null) sb.append('-').append(prerelease);
        if (build != null) sb.append('+').append(build);
        return sb.toString();
    }
}
