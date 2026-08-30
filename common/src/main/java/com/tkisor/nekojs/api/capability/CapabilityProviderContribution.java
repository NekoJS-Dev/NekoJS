package com.tkisor.nekojs.api.capability;

import com.tkisor.nekojs.api.plugin.PluginIdentity;
import com.tkisor.nekojs.api.surface.EnvironmentScope;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record CapabilityProviderContribution(
        PluginIdentity provider,
        String capabilityName,
        int priority,
        Object implementation,
        EnvironmentScope environmentScope,
        Map<String, Object> services
) {
    public CapabilityProviderContribution {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(capabilityName, "capabilityName");
        services = Map.copyOf(services == null ? Map.of() : services);
    }
}
