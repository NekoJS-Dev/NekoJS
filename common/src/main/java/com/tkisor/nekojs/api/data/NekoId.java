package com.tkisor.nekojs.api.data;

import com.tkisor.nekojs.NekoJSCommon;

public record NekoId(String namespace, String path) {
    public NekoId {
        if (namespace == null || namespace.isBlank()) {
            throw new IllegalArgumentException("ID namespace cannot be blank");
        }
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("ID path cannot be blank");
        }
    }

    public static NekoId of(String value) {
        int separator = value.indexOf(':');
        if (separator >= 0) {
            return new NekoId(value.substring(0, separator), value.substring(separator + 1));
        }
        return new NekoId(NekoJSCommon.MODID, value);
    }

    public static NekoId of(String namespace, String path) {
        return new NekoId(namespace, path);
    }

    public String getNamespace() {
        return namespace;
    }

    public String getPath() {
        return path;
    }

    public String asString() {
        return toString();
    }

    @Override
    public String toString() {
        return namespace + ":" + path;
    }
}
