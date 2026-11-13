package com.tkisor.nekojs.api.data;

import org.jspecify.annotations.NonNull;

/**
 * A platform-independent resource locator that stands in for vanilla's Identifier.
 */
public record ScriptId(String namespace, String path) {

    public static ScriptId of(String namespace, String path) {
        return new ScriptId(namespace, path);
    }

    @Override
    public @NonNull String toString() {
        return namespace + ":" + path;
    }
}