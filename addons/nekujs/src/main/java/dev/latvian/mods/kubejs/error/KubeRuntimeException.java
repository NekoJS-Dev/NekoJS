package dev.latvian.mods.kubejs.error;

import dev.latvian.mods.kubejs.script.ConsoleLine;
import dev.latvian.mods.kubejs.script.SourceLine;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public class KubeRuntimeException extends RuntimeException {
    private SourceLine source = SourceLine.UNKNOWN;
    public final Map<String, Object> customData = new LinkedHashMap<>();
    public KubeRuntimeException(String message) { super(message); }
    public KubeRuntimeException(String message, Throwable cause) { super(message, cause); }
    public KubeRuntimeException(Throwable cause) { super(cause); }
    public KubeRuntimeException source(SourceLine line) {
        Objects.requireNonNull(line);
        if (source.isUnknown()) source = line;
        return this;
    }
    public SourceLine source() { return source; }
    public ConsoleLine apply(ConsoleLine line) { line.source(source); line.customData.putAll(customData); return line; }
}
