package com.tkisor.nekojs.core.pack.sync;

import com.google.gson.JsonObject;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.util.List;

/**
 * Signs packs with Ed25519 through JDK {@code java.security}. Used by tests and by offline signing
 * tools for pack authors; the server only passes along the signature block embedded in the manifest,
 * and signing happens in the packaging step.
 */
public final class PackSigner {

    private PackSigner() {}

    public static KeyPair generateKeyPair() {
        try {
            return KeyPairGenerator.getInstance(PackSignaturePayload.ALGORITHM).generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Ed25519 unavailable", e);
        }
    }

    /**
     * Encodes an X.509/SPKI public key as base64, the manifest {@code signature.publicKey} format.
     */
    public static String encodePublicKey(PublicKey publicKey) {
        return PackSignaturePayload.encodeBase64(publicKey.getEncoded());
    }

    /**
     * Signs a pack snapshot and returns the signature block to embed in the manifest:
     * {@code {algorithm, keyId, publicKey, signature}}.
     */
    public static JsonObject sign(
        String keyId,
        KeyPair keyPair,
        String syncId,
        String scopeName,
        String manifestJson,
        List<PackContentFile> files
    ) {
        byte[] payload = PackSignaturePayload.build(syncId, scopeName, manifestJson, files);
        byte[] signature;
        try {
            Signature instance = Signature.getInstance(PackSignaturePayload.ALGORITHM);
            instance.initSign(keyPair.getPrivate());
            instance.update(payload);
            signature = instance.sign();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sign pack " + syncId, e);
        }
        JsonObject block = new JsonObject();
        block.addProperty("algorithm", PackSignaturePayload.ALGORITHM);
        block.addProperty("keyId", keyId);
        block.addProperty("publicKey", encodePublicKey(keyPair.getPublic()));
        block.addProperty("signature", PackSignaturePayload.encodeBase64(signature));
        return block;
    }

    /**
     * Compatibility overload: signs with the private key alone; the caller encodes and embeds the public
     * key separately.
     */
    public static JsonObject sign(
        String keyId,
        PrivateKey privateKey,
        PublicKey publicKey,
        String syncId,
        String scopeName,
        String manifestJson,
        List<PackContentFile> files
    ) {
        return sign(keyId, new KeyPair(publicKey, privateKey), syncId, scopeName, manifestJson, files);
    }
}
