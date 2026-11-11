package dev.latvian.mods.kubejs.script;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Captured diagnostic metadata, not an evaluator or script lifetime owner. */
public class ConsoleLine {
    public final String message;
    public final Throwable throwable;
    public SourceLine sourceLine = SourceLine.UNKNOWN;
    public final Map<String, Object> customData = new LinkedHashMap<>();
    public ConsoleLine(String message, Throwable throwable) { this.message = Objects.requireNonNull(message); this.throwable = throwable; }
    public ConsoleLine source(SourceLine source) { sourceLine = Objects.requireNonNull(source); return this; }
}
