package com.tkisor.nekojs.api.capability;

import java.util.Objects;

public record ActiveCapability(
        String name,
        CapabilityImplementationMode mode,
        Object implementation
) {
    public ActiveCapability {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(implementation, "implementation");
    }
}
