package com.tkisor.nekojs.core.module;

import java.nio.file.Path;

/** Trust approval carried by the preparation/cache dependency chain. */
@FunctionalInterface
public interface NekoTrustContext {
    NekoTrustApprovedSource approvalFor(Path file);

    /** A verified remote pack file that may be admitted to one runtime candidate. */
    record RemoteSource(Path file, String packId, String keyId) {
        public RemoteSource {
            if (file == null) throw new NullPointerException("file");
            if (packId == null || packId.isBlank()) throw new IllegalArgumentException("packId");
            if (keyId == null || keyId.isBlank()) throw new IllegalArgumentException("keyId");
        }
    }

    static NekoTrustContext local() {
        return NekoTrustApprovedSource::local;
    }
}
