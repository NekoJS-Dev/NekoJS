package com.tkisor.nekojs.api.plugin;

import java.util.Objects;

public record OwnedPlugin(PluginIdentity identity, String displayName) {
    public OwnedPlugin {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(displayName, "displayName");
    }
}
