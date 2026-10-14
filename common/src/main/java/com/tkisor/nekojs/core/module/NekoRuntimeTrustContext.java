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
    private volatile Map<String, NekoTrustApprovedSource> remoteApprovals = Map.of();
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
        String root = NekoCanonicalPath.of(remoteRoot);
        Map<String, NekoTrustApprovedSource> replacement = new java.util.LinkedHashMap<>();
        for (RemoteSource source : sources == null ? java.util.List.<RemoteSource>of() : sources) {
            String subject = NekoCanonicalPath.of(source.file());
            if (!NekoCanonicalPath.isWithin(subject, root)) {
                throw new IllegalArgumentException("Remote source is outside the active cache root: " + source.file());
            }
            replacement.put(subject, source.unsignedAuthorized()
                    ? NekoTrustApprovedSource.remoteUnsigned(source.file(), source.packId())
                    : NekoTrustApprovedSource.remote(source.file(), source.packId(), source.keyId()));
        }
        protectedRoots.add(root);
        remoteApprovals = Map.copyOf(replacement);
        remotePaths.addAll(replacement.keySet());
    }

    /** Revoke credentials and keep the supplied cache root protected against local fallback. */
    public synchronized void revokeRemoteSources(Path remoteRoot) {
        remoteApprovals = Map.of();
        if (remoteRoot != null) {
            protectedRoots.add(NekoCanonicalPath.of(remoteRoot));
        }
    }

    @Override
    public NekoTrustApprovedSource approvalFor(Path file) {
        String subject = NekoCanonicalPath.of(file);
        if (remotePaths.contains(subject) || protectedRoots.stream()
                .anyMatch(root -> NekoCanonicalPath.isWithin(subject, root))) {
            return remoteApprovals.get(subject);
        }
        return NekoTrustApprovedSource.local(file);
    }
}
