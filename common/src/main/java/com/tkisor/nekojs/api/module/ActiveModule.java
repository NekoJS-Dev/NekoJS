package com.tkisor.nekojs.api.module;

import java.util.Objects;

/**
 * An activated module: its descriptor plus the module implementation instance.
 *
 * @param descriptor     module descriptor, never {@code null}
 * @param implementation module implementation instance, may be {@code null}
 */
public record ActiveModule(
        ApiModuleDescriptor descriptor,
        Object implementation
) {
    public ActiveModule {
        Objects.requireNonNull(descriptor, "descriptor");
    }
}
