package com.tkisor.nekojs.core.pack.sync;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.security.KeyPair;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Signature verifier tests: sign and verify round trip, a tampered manifest, a keyId pinning
 * conflict (constant-time path), unsigned packs gated by allowUnsigned, and the algorithm
 * allowlist (Ed25519 only).
 */
class PackSignatureVerifierTest {

    @TempDir
    Path trustDir;

    private static final List<PackContentFile> FILES = List.of(
        new PackContentFile("client_scripts/hud.js", "hud()".getBytes()),
        new PackContentFile("server_scripts/tick.js", "tick()".getBytes()));

    @Test
    void validSignatureRoundTrip() {
        KeyPair keyPair = PackSigner.generateKeyPair();
        String manifest = signedManifest("packs:demo", "GLOBAL", "author-key", keyPair);
        PackSyncTrustStore trustStore = new PackSyncTrustStore(trustDir.resolve("trust.json"));

        PackSignatureVerifier.Result result = PackSignatureVerifier.verify(
            "packs:demo", "GLOBAL", manifest, FILES, false, trustStore);

        assertTrue(result.valid());
        assertTrue(result.signed());
        assertEquals("author-key", result.keyId());
        assertNotNull(result.fingerprint());
    }

    @Test
    void tamperedManifestFails() {
        KeyPair keyPair = PackSigner.generateKeyPair();
        String manifest = signedManifest("packs:demo", "GLOBAL", "author-key", keyPair);
        JsonObject root = JsonParser.parseString(manifest).getAsJsonObject();
        root.addProperty("version", "tampered");
        String tampered = root.toString();

        PackSignatureVerifier.Result result = PackSignatureVerifier.verify(
            "packs:demo", "GLOBAL", tampered, FILES, false, new PackSyncTrustStore(trustDir.resolve("t1.json")));

        assertFalse(result.valid());
    }

    @Test
    void tamperedFileContentFails() {
        KeyPair keyPair = PackSigner.generateKeyPair();
        String manifest = signedManifest("packs:demo", "GLOBAL", "author-key", keyPair);
        List<PackContentFile> tamperedFiles = List.of(
            new PackContentFile("client_scripts/hud.js", "hudMalicious()".getBytes()),
            new PackContentFile("server_scripts/tick.js", "tick()".getBytes()));

        PackSignatureVerifier.Result result = PackSignatureVerifier.verify(
            "packs:demo", "GLOBAL", manifest, tamperedFiles, false, new PackSyncTrustStore(trustDir.resolve("t2.json")));

        assertFalse(result.valid());
    }

    @Test
    void pinnedKeyIdWithDifferentKeyFails() {
        KeyPair authorKey = PackSigner.generateKeyPair();
        KeyPair attackerKey = PackSigner.generateKeyPair();
        String manifest = signedManifest("packs:demo", "GLOBAL", "author-key", authorKey);

        // The trust store pins the author key under its keyId, but the pack carries the attacker key,
        // so verification rejects it and a silent key swap cannot pass.
        PackSyncTrustStore trustStore = new PackSyncTrustStore(trustDir.resolve("t3.json"));
        trustStore.trustPublicKey("author-key", PackSigner.encodePublicKey(attackerKey.getPublic()), "fp", "srv");

        PackSignatureVerifier.Result result = PackSignatureVerifier.verify(
            "packs:demo", "GLOBAL", manifest, FILES, false, trustStore);

        assertFalse(result.valid());
        assertTrue(result.reason().contains("does not match pinned key"));
    }

    @Test
    void pinnedKeyIdWithSameKeyStillValid() {
        KeyPair authorKey = PackSigner.generateKeyPair();
        String manifest = signedManifest("packs:demo", "GLOBAL", "author-key", authorKey);

        PackSyncTrustStore trustStore = new PackSyncTrustStore(trustDir.resolve("t4.json"));
        trustStore.trustPublicKey(
            "author-key", PackSigner.encodePublicKey(authorKey.getPublic()), "fp", "srv");

        PackSignatureVerifier.Result result = PackSignatureVerifier.verify(
            "packs:demo", "GLOBAL", manifest, FILES, false, trustStore);

        assertTrue(result.valid());
    }

    @Test
    void unsignedPackControlledByAllowUnsigned() {
        String unsigned = "{\"id\": \"demo\", \"version\": \"1.0.0\"}";
        PackSyncTrustStore trustStore = new PackSyncTrustStore(trustDir.resolve("t5.json"));

        PackSignatureVerifier.Result rejected = PackSignatureVerifier.verify(
            "packs:demo", "GLOBAL", unsigned, FILES, false, trustStore);
        assertFalse(rejected.valid());
        assertFalse(rejected.signed());

        PackSignatureVerifier.Result accepted = PackSignatureVerifier.verify(
            "packs:demo", "GLOBAL", unsigned, FILES, true, trustStore);
        assertTrue(accepted.valid());
        assertFalse(accepted.signed());
    }

    @Test
    void unsupportedAlgorithmFails() {
        KeyPair keyPair = PackSigner.generateKeyPair();
        String manifest = signedManifest("packs:demo", "GLOBAL", "author-key", keyPair);
        JsonObject root = JsonParser.parseString(manifest).getAsJsonObject();
        root.getAsJsonObject("signature").addProperty("algorithm", "RSA");
        String wrongAlgo = root.toString();

        PackSignatureVerifier.Result result = PackSignatureVerifier.verify(
            "packs:demo", "GLOBAL", wrongAlgo, FILES, false, new PackSyncTrustStore(trustDir.resolve("t6.json")));

        assertFalse(result.valid());
        assertTrue(result.reason().contains("unsupported signature algorithm"));
    }

    /** Builds a manifest with a signature block: it signs the unsigned text first, because
     * verification removes the signature key while canonicalizing. */
    private static String signedManifest(String syncId, String scope, String keyId, KeyPair keyPair) {
        String unsigned = "{\"id\": \"demo\", \"version\": \"1.0.0\", \"authors\": [\"tester\"]}";
        JsonObject signature = PackSigner.sign(keyId, keyPair, syncId, scope, unsigned, FILES);
        JsonObject root = JsonParser.parseString(unsigned).getAsJsonObject();
        root.add("signature", signature);
        return root.toString();
    }
}
