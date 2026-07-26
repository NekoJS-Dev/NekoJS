package com.tkisor.nekojs.api.surface;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record LoaderVersion(List<Integer> segments, String qualifier) implements Comparable<LoaderVersion> {

    private static final Pattern LOADER_VER = Pattern.compile(
            "^(?<segments>\\d+(?:\\.\\d+){0,3})(?:-(?<qualifier>[A-Za-z][A-Za-z0-9]*))?$");

    public LoaderVersion {
        segments = List.copyOf(segments == null ? List.of() : segments);
    }

    public static LoaderVersion parse(String raw) {
        Objects.requireNonNull(raw, "raw");
        Matcher m = LOADER_VER.matcher(raw);
        if (!m.matches()) throw new IllegalArgumentException("invalid loader version: " + raw);
        String[] parts = m.group("segments").split("\\.");
        List<Integer> segs = new ArrayList<>();
        for (String p : parts) {
            segs.add(Integer.parseInt(p));
        }
        String q = m.group("qualifier");
        return new LoaderVersion(segs, q);
    }

    @Override
    public int compareTo(LoaderVersion other) {
        int maxLen = Math.max(this.segments.size(), other.segments.size());
        for (int i = 0; i < maxLen; i++) {
            int a = i < this.segments.size() ? this.segments.get(i) : 0;
            int b = i < other.segments.size() ? other.segments.get(i) : 0;
            int c = Integer.compare(a, b);
            if (c != 0) return c;
        }
        // Same numeric segments: prerelease < release
        if (this.qualifier == null && other.qualifier == null) return 0;
        if (this.qualifier == null) return 1;  // release > prerelease
        if (other.qualifier == null) return -1; // prerelease < release
        // Both have qualifier: compare by Unicode code-point
        return this.qualifier.compareTo(other.qualifier);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < segments.size(); i++) {
            if (i > 0) sb.append('.');
            sb.append(segments.get(i));
        }
        if (qualifier != null) sb.append('-').append(qualifier);
        return sb.toString();
    }
}
