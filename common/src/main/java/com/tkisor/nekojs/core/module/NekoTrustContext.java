package com.tkisor.nekojs.core.module;

import java.nio.file.Path;

/** Trust approval carried by the preparation/cache dependency chain. */
@FunctionalInterface
public interface NekoTrustContext {
    NekoTrustApprovedSource approvalFor(Path file);

    /** A verified remote pack file that may be admitted to one runtime candidate. */
    record RemoteSource(Path file, String packId, String keyId, boolean unsignedAuthorized) {
        public RemoteSource(Path file, String packId, String keyId) { this(file, packId, keyId, false); }
        public RemoteSource {
            if (file == null) throw new NullPointerException("file");
            if (packId == null || packId.isBlank()) throw new IllegalArgumentException("packId");
            if (!unsignedAuthorized && (keyId == null || keyId.isBlank())) throw new IllegalArgumentException("keyId");
            if (unsignedAuthorized && keyId != null) throw new IllegalArgumentException("Unsigned source carries key evidence");
        }
        public static RemoteSource unsigned(Path file, String packId) { return new RemoteSource(file, packId, null, true); }
    }

    static NekoTrustContext local() {
        return NekoTrustApprovedSource::local;
    }
}
