package com.tkisor.nekojs.api.module;

import java.util.Objects;

/**
 * A module that was not activated: its descriptor, the reason and a detail message.
 *
 * @param descriptor module descriptor, never {@code null}
 * @param reason     reason it was not activated, never {@code null}
 * @param detail     detail message, may be {@code null}
 */
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
