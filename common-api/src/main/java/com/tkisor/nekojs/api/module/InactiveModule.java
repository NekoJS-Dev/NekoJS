package com.tkisor.nekojs.api.module;

import java.util.Objects;

public record InactiveModule(
        ApiModuleDescriptor descriptor,
        InactiveReason reason,
        String detail
) {
    public InactiveModule {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(reason, "reason");
    }
}
