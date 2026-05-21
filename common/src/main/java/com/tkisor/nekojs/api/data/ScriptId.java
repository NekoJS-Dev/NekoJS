package com.tkisor.nekojs.api.data;

import org.jspecify.annotations.NonNull;

/**
 * 平台无关的资源定位符，替代原版的 Identifier
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