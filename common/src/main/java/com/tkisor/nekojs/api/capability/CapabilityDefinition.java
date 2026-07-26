package com.tkisor.nekojs.api.capability;

import java.util.Objects;
import java.util.Set;

public record CapabilityDefinition(
        String name,
        CapabilityImplementationMode mode,
        Set<String> providerHints
) {
    public CapabilityDefinition {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(mode, "mode");
        providerHints = Set.copyOf(providerHints == null ? Set.of() : providerHints);
    }
}
