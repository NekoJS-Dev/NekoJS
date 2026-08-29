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
 * 远端包签名校验器（Katton {@code RemoteScriptSignatureVerifier} 移植）。
 *
 * <ul>
 *   <li>仅支持 Ed25519；manifest.signature = {algorithm, keyId, publicKey, signature}。</li>
 *   <li>公钥解析：信任库按 keyId pinning 的公钥优先；两者并存时嵌入公钥必须与 pinning
 *       字节一致（{@link MessageDigest#isEqual} 常量时间比较）——防密钥静默轮换。</li>
 *   <li>无 signature 块 = 未签名包：仅当 {@code allowUnsigned} 时有效（否则拒绝并断连）。</li>
 * </ul>
 */
public final class PackSignatureVerifier {

    /** 校验结果：signed=false 表示包未携带签名块（是否放行由 allowUnsigned 决定）。 */
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

    /** 解析 manifest 顶层 signature 对象；缺失/非对象返回 null（未签名包）。 */
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
