package com.tkisor.nekojs.api.module;

import java.util.Objects;

public record ActiveModule(
        ApiModuleDescriptor descriptor,
        Object implementation
) {
    public ActiveModule {
        Objects.requireNonNull(descriptor, "descriptor");
    }
}
