package com.tkisor.nekojs.api.surface;

import java.util.Objects;

public record ApiSymbolId(String kind, String qualifiedName) {

    public ApiSymbolId {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(qualifiedName, "qualifiedName");
        if (kind.isBlank()) throw new IllegalArgumentException("kind must not be blank");
        if (qualifiedName.isBlank()) throw new IllegalArgumentException("qualifiedName must not be blank");
    }

    public String value() {
        return kind + ":" + qualifiedName;
    }

    public static ApiSymbolId parse(String raw) {
        Objects.requireNonNull(raw, "raw");
        if (raw.isBlank()) throw new IllegalArgumentException("symbol id must not be blank");
        int idx = raw.indexOf(':');
        if (idx < 0) throw new IllegalArgumentException("symbol id must contain ':' separator: " + raw);
        String kind = raw.substring(0, idx);
        String qualifiedName = raw.substring(idx + 1);
        return new ApiSymbolId(kind, qualifiedName);
    }

    @Override
    public String toString() {
        return value();
    }
}
