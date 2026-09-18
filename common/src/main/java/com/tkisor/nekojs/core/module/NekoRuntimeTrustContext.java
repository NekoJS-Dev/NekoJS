package com.tkisor.nekojs.core.module;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runtime-owned trust context used by the production assembly.
 *
 * <p>Local files use the normal local-trusted rule. Files admitted from a server pack are
 * remembered as remote paths and only return a credential after the pack activation owner has
 * supplied verified key evidence. Revocation removes the credential while retaining the remote
 * path marker, so an inactive cached pack cannot fall back to local trust.
 */
public final class NekoRuntimeTrustContext implements NekoTrustContext {
    private final Set<String> remotePaths = ConcurrentHashMap.newKeySet();
    private final Map<String, NekoTrustApprovedSource> remoteApprovals = new ConcurrentHashMap<>();

    private NekoRuntimeTrustContext() {}

    public static NekoRuntimeTrustContext local() {
        return new NekoRuntimeTrustContext();
    }

    /** Admit only files from a verified and activated remote pack. */
    public void authorizeRemoteSources(Collection<RemoteSource> sources) {
        if (sources == null) return;
        for (RemoteSource source : sources) {
            String subject = NekoTrustApprovedSource.subjectOf(source.file());
            remotePaths.add(subject);
            remoteApprovals.put(subject,
                    NekoTrustApprovedSource.remote(source.file(), source.packId(), source.keyId()));
        }
    }

    /** Revoke all currently issued remote credentials; cached files remain non-local by origin. */
    public void revokeRemoteSources() {
        remoteApprovals.clear();
    }

    @Override
    public NekoTrustApprovedSource approvalFor(Path file) {
        String subject = NekoTrustApprovedSource.subjectOf(file);
        if (remotePaths.contains(subject)) {
            return remoteApprovals.get(subject);
        }
        return NekoTrustApprovedSource.local(file);
    }
}
