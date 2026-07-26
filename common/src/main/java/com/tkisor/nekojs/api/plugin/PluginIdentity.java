package com.tkisor.nekojs.api.plugin;

import java.net.URI;
import java.util.Objects;

public record PluginIdentity(String ownerId, String pluginClassName, URI codeSource) {
    public PluginIdentity {
        if (ownerId == null || ownerId.isBlank()) throw new IllegalArgumentException("ownerId");
        if (pluginClassName == null || pluginClassName.isBlank()) throw new IllegalArgumentException("pluginClassName");
        Objects.requireNonNull(codeSource, "codeSource");
    }
}
