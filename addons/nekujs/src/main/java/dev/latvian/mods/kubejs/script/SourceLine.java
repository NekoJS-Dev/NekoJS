package dev.latvian.mods.kubejs.script;

import dev.latvian.mods.rhino.Context;

public record SourceLine(String source, int line) {
    public static final SourceLine UNKNOWN = new SourceLine("", 0);
    public SourceLine {
        source = source == null ? "" : source;
        line = Math.max(0, line);
    }
    public static SourceLine of(String source, int line) { return new SourceLine(source, line); }
    public static SourceLine of(Context context) {
        if (context == null) return UNKNOWN;
        throw new UnsupportedOperationException("[NEKO-4281] Native source-position service is not available for this legacy context");
    }
    public boolean isUnknown() { return source.isEmpty(); }
    @Override public String toString() { return source.isEmpty() ? "unknown" : source + "#" + line; }
}
