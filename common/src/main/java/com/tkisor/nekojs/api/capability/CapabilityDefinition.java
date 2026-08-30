package com.tkisor.nekojs.api.capability;

import com.tkisor.nekojs.api.surface.ApiVersion;
import com.tkisor.nekojs.api.surface.EnvironmentScope;

import java.util.Objects;
import java.util.Set;

public record CapabilityDefinition(
        String name,
        ApiVersion contractVersion,
        CapabilityImplementationMode mode,
        ProviderPolicy providerPolicy,
        Set<String> allowedProviderOwners,
        EnvironmentScope environmentScope,
        Set<String> requiredServiceKeys,
        Set<String> providerHints
) {
    public CapabilityDefinition {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(contractVersion, "contractVersion");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(providerPolicy, "providerPolicy");
        allowedProviderOwners = Set.copyOf(allowedProviderOwners == null ? Set.of() : allowedProviderOwners);
        requiredServiceKeys = Set.copyOf(requiredServiceKeys == null ? Set.of() : requiredServiceKeys);
        providerHints = Set.copyOf(providerHints == null ? Set.of() : providerHints);

        if (providerPolicy == ProviderPolicy.CORE_ONLY && !allowedProviderOwners.isEmpty()) {
            throw new IllegalArgumentException("CORE_ONLY policy must not have allowedProviderOwners");
        }
        if (providerPolicy == ProviderPolicy.ALLOWLIST && allowedProviderOwners.isEmpty()) {
            throw new IllegalArgumentException("ALLOWLIST policy must have at least one allowedProviderOwner");
        }
    }
}
