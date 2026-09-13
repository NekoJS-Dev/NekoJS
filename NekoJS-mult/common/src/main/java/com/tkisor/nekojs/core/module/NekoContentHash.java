package com.tkisor.nekojs.core.module;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 内容指纹工具（工单 11：Script Preparation 的稳定 cache key 输入之一）。
 *
 * <p>历史缺陷：仅凭 (modifiedMillis, size) 无法区分「同一时间戳刻度内对等长文件的覆盖写入」，
 * 粗粒度时间戳文件系统（部分 Windows / FAT / 容器挂载）会因此误判未变化，继续返回旧编译模块。
 * 修复采用 SHA-256 内容指纹：源码在 prepare 前已完整读入（{@code Files.readString}），
 * SHA-256 的额外开销相对模块编译管线可忽略；64-bit hash 在长期运行、大量模块的场景下仍有
 * 生日碰撞的微小概率，一旦碰撞会静默返回错误代码。
 */
public final class NekoContentHash {

    private NekoContentHash() {}

    public static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // JDK 规范要求 SHA-256 算法必须存在；这里作为环境缺陷快速失败。
            throw new IllegalStateException("SHA-256 digest is not available on this JVM", e);
        }
    }
}
