package com.tkisor.nekojs.api.plugin;

import com.tkisor.nekojs.api.NekoJSPlugin;

import java.util.Objects;

public record OwnedPlugin(PluginIdentity identity, NekoJSPlugin plugin) {
    public OwnedPlugin {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(plugin, "plugin");
    }
}
