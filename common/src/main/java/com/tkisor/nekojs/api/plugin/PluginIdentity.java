package com.tkisor.nekojs.api.plugin;

import java.util.Objects;

public record PluginIdentity(String owner, String pluginId) {
    public PluginIdentity {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(pluginId, "pluginId");
    }
}
