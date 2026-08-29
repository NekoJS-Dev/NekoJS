package com.tkisor.nekojs.api.plugin;

import java.net.URI;
import java.util.Objects;

/**
 * 插件身份：ownerId、插件类名与代码来源 URI。
 *
 * @param ownerId         owner id，不能为空白
 * @param pluginClassName 插件类全限定名，不能为空白
 * @param codeSource      代码来源 URI，不能为 {@code null}
 */
public record PluginIdentity(String ownerId, String pluginClassName, URI codeSource) {
    public PluginIdentity {
        if (ownerId == null || ownerId.isBlank()) throw new IllegalArgumentException("ownerId");
        if (pluginClassName == null || pluginClassName.isBlank()) throw new IllegalArgumentException("pluginClassName");
        Objects.requireNonNull(codeSource, "codeSource");
    }
}
