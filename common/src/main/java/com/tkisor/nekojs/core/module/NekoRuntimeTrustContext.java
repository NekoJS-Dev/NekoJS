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
    private final Set<String> protectedRoots = ConcurrentHashMap.newKeySet();

    private NekoRuntimeTrustContext() {}

    public static NekoRuntimeTrustContext local() {
        return new NekoRuntimeTrustContext();
    }

    /**
     * Replace the active remote credentials and protect the active cache root. Previously known
     * roots remain protected so a server switch cannot turn an old cached file into local trust.
     */
    public synchronized void authorizeRemoteSources(Collection<RemoteSource> sources, Path remoteRoot) {
        if (remoteRoot == null) {
            throw new IllegalArgumentException("remoteRoot");
        }
        String root = NekoTrustApprovedSource.subjectOf(remoteRoot);
        protectedRoots.add(root);
        remotePaths.clear();
        remoteApprovals.clear();
        if (sources == null) return;
        for (RemoteSource source : sources) {
            String subject = NekoTrustApprovedSource.subjectOf(source.file());
            if (!NekoTrustApprovedSource.isWithin(subject, root)) {
                throw new IllegalArgumentException("Remote source is outside the active cache root: " + source.file());
            }
            remotePaths.add(subject);
            remoteApprovals.put(subject,
                    NekoTrustApprovedSource.remote(source.file(), source.packId(), source.keyId()));
        }
    }

    /** Revoke credentials and keep the supplied cache root protected against local fallback. */
    public synchronized void revokeRemoteSources(Path remoteRoot) {
        remoteApprovals.clear();
        remotePaths.clear();
        if (remoteRoot != null) {
            protectedRoots.add(NekoTrustApprovedSource.subjectOf(remoteRoot));
        }
    }

    @Override
    public NekoTrustApprovedSource approvalFor(Path file) {
        String subject = NekoTrustApprovedSource.subjectOf(file);
        if (remotePaths.contains(subject) || protectedRoots.stream()
                .anyMatch(root -> NekoTrustApprovedSource.isWithin(subject, root))) {
            return remoteApprovals.get(subject);
        }
        return NekoTrustApprovedSource.local(file);
    }
}
