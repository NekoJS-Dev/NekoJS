package com.tkisor.nekojs.core.pack.sync;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.List;

/**
 * Verifies signatures on remote packs, ported from Katton's {@code RemoteScriptSignatureVerifier}.
 *
 * <ul>
 *   <li>Only Ed25519 is supported; manifest.signature = {algorithm, keyId, publicKey, signature}.</li>
 *   <li>Public key resolution: a key pinned by keyId in the trust store takes priority; when both
 *       exist, the embedded key must match the pinned bytes ({@link MessageDigest#isEqual},
 *       constant-time), which prevents silent key rotation.</li>
 *   <li>No signature block means an unsigned pack: it is only valid when {@code allowUnsigned} is
 *       set, otherwise it is rejected and the connection drops.</li>
 * </ul>
 */
public final class PackSignatureVerifier {

    /**
     * Verification result. A pack with no signature block has signed=false, and allowUnsigned decides
     * whether it is accepted.
     */
    public record Result(boolean valid, boolean signed, String reason, String keyId, String fingerprint) {

        static Result unsigned(boolean valid, String reason) {
            return new Result(valid, false, reason, null, null);
        }
    }

    private PackSignatureVerifier() {}

    public static Result verify(
        String syncId,
        String scopeName,
        String manifestJson,
        List<PackContentFile> files,
        boolean allowUnsigned,
        PackSyncTrustStore trustStore
    ) {
        try {
            return verifyInternal(syncId, scopeName, manifestJson, files, allowUnsigned, trustStore);
        } catch (Exception e) {
            return new Result(false, true, "signature verification failed: " + e, null, null);
        }
    }

    private static Result verifyInternal(
        String syncId,
        String scopeName,
        String manifestJson,
        List<PackContentFile> files,
        boolean allowUnsigned,
        PackSyncTrustStore trustStore
    ) {
        JsonObject signature = parseSignatureBlock(manifestJson);
        if (signature == null) {
            return allowUnsigned
                ? Result.unsigned(true, "unsigned pack accepted (allowUnsigned)")
                : Result.unsigned(false, "unsigned pack rejected (packSync.allowUnsigned = false)");
        }
        String algorithm = string(signature, "algorithm");
        if (!PackSignaturePayload.ALGORITHM.equals(algorithm)) {
            return new Result(false, true, "unsupported signature algorithm: " + algorithm, string(signature, "keyId"), null);
        }
        String keyId = string(signature, "keyId");
        if (keyId == null || keyId.isBlank()) {
            return new Result(false, true, "missing keyId", null, null);
        }

        String trustedPublicKey = trustStore != null ? trustStore.trustedPublicKey(keyId) : null;
        String embeddedPublicKey = string(signature, "publicKey");
        String publicKeyText = trustedPublicKey != null ? trustedPublicKey : embeddedPublicKey;
        if (publicKeyText == null) {
            return new Result(false, true, "no public key for keyId " + keyId + " (not pinned and not embedded)", keyId, null);
        }
        byte[] publicKeyBytes = PackSignaturePayload.decodeBase64(publicKeyText);
        if (publicKeyBytes == null) {
            return new Result(false, true, "invalid public key encoding for " + keyId, keyId, null);
        }
        byte[] signatureBytes = PackSignaturePayload.decodeBase64(string(signature, "signature"));
        if (signatureBytes == null) {
            return new Result(false, true, "invalid signature encoding for " + keyId, keyId, null);
        }

        String fingerprint = PackSignaturePayload.fingerprint(publicKeyBytes);
        if (trustedPublicKey != null && embeddedPublicKey != null) {
            byte[] embeddedBytes = PackSignaturePayload.decodeBase64(embeddedPublicKey);
            if (embeddedBytes == null) {
                return new Result(false, true, "invalid embedded public key encoding for " + keyId, keyId, fingerprint);
            }
            if (!MessageDigest.isEqual(publicKeyBytes, embeddedBytes)) {
                return new Result(false, true,
                    "embedded public key does not match pinned key for " + keyId + " (key rotation not accepted)", keyId, fingerprint);
            }
        }

        PublicKey publicKey;
        try {
            publicKey = KeyFactory.getInstance(PackSignaturePayload.ALGORITHM)
                .generatePublic(new X509EncodedKeySpec(publicKeyBytes));
        } catch (Exception e) {
            return new Result(false, true, "invalid Ed25519 public key for " + keyId, keyId, fingerprint);
        }

        byte[] payload = PackSignaturePayload.build(syncId, scopeName, manifestJson, files);
        boolean valid;
        try {
            Signature instance = Signature.getInstance(PackSignaturePayload.ALGORITHM);
            instance.initVerify(publicKey);
            instance.update(payload);
            valid = instance.verify(signatureBytes);
        } catch (Exception e) {
            valid = false;
        }
        return valid
            ? new Result(true, true, "signature verified", keyId, fingerprint)
            : new Result(false, true, "signature mismatch for " + keyId, keyId, fingerprint);
    }

    /**
     * Parses the top-level signature object of the manifest. Returns null when it is missing or is not an
     * object, which means the pack is unsigned.
     */
    static JsonObject parseSignatureBlock(String manifestJson) {
        try {
            JsonElement root = JsonParser.parseString(manifestJson);
            if (!root.isJsonObject()) return null;
            JsonElement signature = root.getAsJsonObject().get("signature");
            return signature != null && signature.isJsonObject() ? signature.getAsJsonObject() : null;
        } catch (Exception e) {
            return null;
        }
    }

    static String string(JsonObject root, String key) {
        JsonElement el = root.get(key);
        return el != null && el.isJsonPrimitive() && el.getAsJsonPrimitive().isString() ? el.getAsString() : null;
    }
}
