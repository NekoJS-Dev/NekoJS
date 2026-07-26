package com.tkisor.nekojs.api.capability;

import com.tkisor.nekojs.api.plugin.PluginIdentity;

import java.util.Objects;

public record CapabilityProviderContribution(
        PluginIdentity provider,
        String capabilityName,
        int priority
) {
    public CapabilityProviderContribution {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(capabilityName, "capabilityName");
    }
}
