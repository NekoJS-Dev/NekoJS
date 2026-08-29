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
 * 包签名器（Ed25519，JDK {@code java.security}）。供测试与包作者离线签名工具使用——
 * 服务器只透传 manifest 内嵌的签名块，签名发生在打包阶段。
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

    /** X.509/SPKI 编码公钥 → base64（manifest {@code signature.publicKey} 格式）。 */
    public static String encodePublicKey(PublicKey publicKey) {
        return PackSignaturePayload.encodeBase64(publicKey.getEncoded());
    }

    /**
     * 对包快照签名并返回 manifest 可嵌入的签名块：
     * {@code {algorithm, keyId, publicKey, signature}}。
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

    /** 兼容重载：只持有私钥时签名（公钥由调用方另行编码嵌入）。 */
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
