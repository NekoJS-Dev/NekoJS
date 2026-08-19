package com.tkisor.nekojs.core.pack.sync;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * 包签名 payload 构造与 manifest 规范化（签名/验签共用）。
 *
 * <p>签名 payload = 0x00 分隔的字节流：域分隔串 {@code nekojs-pack-signature-v1} +
 * syncId + 作用域枚举名 + <b>去掉 {@code signature} 键</b>的规范化 manifest JSON
 * （Gson 重新序列化，键序与原始格式差异被吸收）+ 按路径排序的文件流
 * （每项：相对路径 + 0x00 + 字节 + 0x00）。
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

    /** 去掉 signature 键的规范化 manifest；解析失败时原样返回（验签会因签名不匹配而失败）。 */
    static String canonicalManifestWithoutSignature(String manifestJson) {
        try {
            JsonObject root = JsonParser.parseString(manifestJson).getAsJsonObject();
            root.remove("signature");
            return root.toString();
        } catch (Exception e) {
            return manifestJson;
        }
    }

    /** base64 解码：先标准表、再 URL 表（兼容两种编码习惯）。非法输入返回 null。 */
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

    /** 公钥指纹：SHA-256 十六进制冒号分隔（信任库展示用）。 */
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
