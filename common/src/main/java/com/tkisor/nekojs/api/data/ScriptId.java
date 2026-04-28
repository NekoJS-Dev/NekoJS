package com.tkisor.nekojs.api.data;

/**
 * 平台无关的资源定位符，替代原版的 Identifier
 */
public record ScriptId(String namespace, String path) {

    public static ScriptId of(String namespace, String path) {
        return new ScriptId(namespace, path);
    }

    @Override
    public String toString() {
        return namespace + ":" + path;
    }
}