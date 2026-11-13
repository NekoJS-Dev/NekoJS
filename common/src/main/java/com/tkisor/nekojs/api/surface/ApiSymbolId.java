package com.tkisor.nekojs.api.surface;

import java.util.Objects;

/**
 * Stable identifier of an API symbol, in the form {@code kind:qualifiedName}.
 *
 * @param kind          symbol kind (such as {@code global} or {@code member})
 * @param qualifiedName qualified name (such as {@code ID.of})
 */
public record ApiSymbolId(String kind, String qualifiedName) {

    public ApiSymbolId {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(qualifiedName, "qualifiedName");
        if (kind.isBlank()) throw new IllegalArgumentException("kind must not be blank");
        if (qualifiedName.isBlank()) throw new IllegalArgumentException("qualifiedName must not be blank");
    }

    /** Returns the {@code "kind:qualifiedName"} string. */
    public String value() {
        return kind + ":" + qualifiedName;
    }

    /** Parses a {@code "kind:qualifiedName"} string; throws when the {@code ':'} separator is missing. */
    public static ApiSymbolId parse(String raw) {
        Objects.requireNonNull(raw, "raw");
        if (raw.isBlank()) throw new IllegalArgumentException("symbol id must not be blank");
        int idx = raw.indexOf(':');
        if (idx < 0) throw new IllegalArgumentException("symbol id must contain ':' separator: " + raw);
        String kind = raw.substring(0, idx);
        String qualifiedName = raw.substring(idx + 1);
        return new ApiSymbolId(kind, qualifiedName);
    }

    /** Returns the {@code "kind:qualifiedName"} string. */
    @Override
    public String toString() {
        return value();
    }
}
