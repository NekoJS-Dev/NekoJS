package com.tkisor.nekojs.core.pack.sync;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * Builds the pack signature payload and canonicalizes the manifest, shared by signing and
 * verification.
 *
 * <p>The payload is a 0x00-separated byte stream: the domain string
 * {@code nekojs-pack-signature-v1}, the syncId, the scope enum name, the canonical manifest JSON
 * <b>without the {@code signature} key</b> (re-serialized by Gson, which absorbs differences in key
 * order and original formatting) and the files in path order (each: relative path + 0x00 + bytes +
 * 0x00).
 */
final class PackSignaturePayload {

    static final String DOMAIN = "nekojs-pack-signature-v1";
    static final String ALGORITHM = "Ed25519";

    private PackSignaturePayload() {}

    static byte[] build(String syncId, String scopeName, String manifestJson, java.util.List<PackContentFile> files) {
        MessageDigest digest = PackHasher.sha256();
        digest.update(DOMAIN.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
        digest.update(syncId.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
        digest.update(scopeName.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
        digest.update(canonicalManifestWithoutSignature(manifestJson).getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
        for (PackContentFile file : PackHasher.sortedByPath(files)) {
            digest.update(file.relativePath().getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(file.bytes());
            digest.update((byte) 0);
        }
        return digest.digest();
    }

    /**
     * Canonical manifest with the signature key removed. Returns the input unchanged when parsing fails;
     * verification then fails on the signature mismatch.
     */
    static String canonicalManifestWithoutSignature(String manifestJson) {
        try {
            JsonObject root = JsonParser.parseString(manifestJson).getAsJsonObject();
            root.remove("signature");
            return root.toString();
        } catch (Exception e) {
            return manifestJson;
        }
    }

    /**
     * Decodes base64, trying the standard alphabet and then the URL alphabet, since both encodings are
     * used. Returns null for invalid input.
     */
    static byte[] decodeBase64(String text) {
        if (text == null) return null;
        try {
            return Base64.getDecoder().decode(text);
        } catch (IllegalArgumentException ignored) {
            try {
                return Base64.getUrlDecoder().decode(text);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }

    static String encodeBase64(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    /** Public key fingerprint: SHA-256 as colon-separated hex, shown by the trust store. */
    static String fingerprint(byte[] publicKeyBytes) {
        byte[] digest = PackHasher.sha256().digest(publicKeyBytes);
        StringBuilder sb = new StringBuilder(digest.length * 3);
        for (int i = 0; i < digest.length; i++) {
            if (i > 0) sb.append(':');
            sb.append(String.format("%02x", digest[i]));
        }
        return sb.toString();
    }
}
