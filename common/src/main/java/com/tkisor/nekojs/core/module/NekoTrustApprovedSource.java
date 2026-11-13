package com.tkisor.nekojs.core.module;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Proof that a source is trusted. A local trusted source and a remote explicitly authorized source
 * share the same entry pass through the prepare/resolve/execute stages: the trust decision only
 * sets the authorization result (allow or deny), and never changes the language or the module
 * kind.
 *
 * <p>A locally trusted module and a remote explicitly authorized source go through the same
 * traceable stages:
 * <ul>
 *   <li>The proof binds to one concrete file ({@link #covers(Path)} matches the path exactly). The
 *       assembly side issues it for local files with {@link #local(Path)}; the pack sync side
 *       issues it for a materialized remote file with {@link #remote(Path, String, String)} after
 *       explicit authorization (non-empty keyId).</li>
 *   <li>{@link NekoModulePipeline#prepare(Path, String, NekoTrustApprovedSource)} runs exactly the
 *       same preparation stage for both kinds and produces the same code, mode, cache key and
 *       language.</li>
 *   <li>A denial for a missing or mismatched proof still carries the language id, the mode and the
 *       source location (see {@link NekoModuleError#denied}), so the language stays visible.</li>
 *   <li>The pack sync rejection of an untrusted pack (disconnect plus a {@code /nekojs trust} hint)
 *       happens at the activation gate and never reaches the pipeline; see the existing
 *       {@code PackSyncClient} behavior. This proof only covers the preparation of a materialized
 *       file that is already authorized.</li>
 * </ul>
 *
 * <p>The proof holds no source and no runtime resources. Issuing one only normalizes the path
 * (resolving the real path when it exists); it creates no Graal Context, decides no HostAccess and
 * reads no Minecraft or loader state.
 */
public record NekoTrustApprovedSource(Kind kind, String subject, String packId, String keyId) {
    /**
     * Where the trust comes from: a locally trusted file, or a remote explicit authorization backed
     * by signing key evidence.
     */
    public enum Kind {
        LOCAL_TRUSTED,
        REMOTE_AUTHORIZED,
        REMOTE_UNSIGNED_AUTHORIZED
    }

    public NekoTrustApprovedSource {
        Objects.requireNonNull(kind, "kind");
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("Trust subject path must not be blank");
        }
        // Factory-created subjects are already canonicalized with their Path provider. Re-parsing
        // that string through the default provider would corrupt ZIP/custom-provider paths.
        subject = normalizeSubject(subject);
        if (kind == Kind.REMOTE_AUTHORIZED && (keyId == null || keyId.isBlank())) {
            throw new IllegalArgumentException("Remote authorization requires an explicit key id");
        }
        if (kind == Kind.REMOTE_UNSIGNED_AUTHORIZED && keyId != null) {
            throw new IllegalArgumentException("Unsigned remote approval must not carry signing key evidence");
        }
    }

    /**
     * Proof for a locally trusted file; the subject is the real path when the file exists, otherwise
     * the normalized absolute path.
     */
    public static NekoTrustApprovedSource local(Path file) {
        return new NekoTrustApprovedSource(Kind.LOCAL_TRUSTED, subjectOf(file), null, null);
    }

    /**
     * Proof for a remote explicitly authorized file; the subject is the real path of the
     * materialized file, or the normalized absolute path when it does not exist.
     *
     * @param packId identifies the authorized pack; used for diagnostics and not for the coverage
     *               check
     * @param keyId  signing key id of the explicit authorization; must be non-empty, because no key
     *               evidence means no explicit authorization
     */
    public static NekoTrustApprovedSource remote(Path file, String packId, String keyId) {
        return new NekoTrustApprovedSource(Kind.REMOTE_AUTHORIZED, subjectOf(file), packId, keyId);
    }

    /** Issued by the sync owner only after server trust and explicit unsigned policy approval. */
    public static NekoTrustApprovedSource remoteUnsigned(Path file, String packId) {
        return new NekoTrustApprovedSource(Kind.REMOTE_UNSIGNED_AUTHORIZED, subjectOf(file), packId, null);
    }

    /** Whether this proof covers the given file by exact path match. Other files in the pack need their own proofs. */
    public boolean covers(Path file) {
        return file != null && subject.equals(subjectOf(file));
    }

    /** Human-readable description for diagnostics. It contains no key material; keyId is only an identifier. */
    public String describe() {
        return switch (kind) {
            case LOCAL_TRUSTED -> "local-trusted:" + subject;
            case REMOTE_AUTHORIZED -> "remote-authorized:" + subject
                    + " (pack=" + packId + ", key=" + keyId + ")";
            case REMOTE_UNSIGNED_AUTHORIZED -> "remote-unsigned-authorized:" + subject + " (pack=" + packId + ")";
        };
    }

    static String subjectOf(Path file) {
        return NekoCanonicalPath.of(file);
    }

    private static String normalizeSubject(String subject) {
        return subject.replace('\\', '/');
    }

}
