package com.tkisor.nekojs.api.plugin;

import java.net.URI;
import java.util.Objects;

/**
 * Identity of a plugin: owner id, plugin class name and code source URI.
 *
 * @param ownerId         owner id, must not be blank
 * @param pluginClassName fully qualified plugin class name, must not be blank
 * @param codeSource      code source URI, must not be {@code null}
 */
public record PluginIdentity(String ownerId, String pluginClassName, URI codeSource) {
    public PluginIdentity {
        if (ownerId == null || ownerId.isBlank()) throw new IllegalArgumentException("ownerId");
        if (pluginClassName == null || pluginClassName.isBlank()) throw new IllegalArgumentException("pluginClassName");
        Objects.requireNonNull(codeSource, "codeSource");
    }
}
