package com.tkisor.nekojs.core.module;

import java.nio.file.Path;

/** Trust approval carried by the preparation/cache dependency chain. */
@FunctionalInterface
public interface NekoTrustContext {
    NekoTrustApprovedSource approvalFor(Path file);

    static NekoTrustContext local() {
        return NekoTrustApprovedSource::local;
    }
}
