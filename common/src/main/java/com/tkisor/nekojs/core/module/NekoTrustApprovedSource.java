package com.tkisor.nekojs.core.module;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Trust 批准凭证：本地受信源与远端显式授权源复用同一 prepare/resolve/execute 阶段的
 * 入场券——trust 决策只影响授权结果（放行/拒绝），不改变语言与模块边界。
 *
 * <p>票据 11 要求本地 trusted 与远端 explicitly-authorized source 走同一套可追踪阶段：
 * <ul>
 *   <li>凭证绑定到具体文件（{@link #covers(Path)} 精确匹配）：本地文件由装配侧以
 *       {@link #local(Path)} 签发；远端包物化后的文件由包同步侧在显式授权（非空 keyId）
 *       后以 {@link #remote(Path, String, String)} 签发；</li>
 *   <li>{@link NekoModulePipeline#prepare(Path, String, NekoTrustApprovedSource)} 对两种
 *       kind 执行完全相同的准备阶段，产物（code/mode/cache key/language）一致；</li>
 *   <li>缺失/错配凭证的拒绝错误仍携带 language id、mode 与源码位置（见
 *       {@link NekoModuleError#denied})，不隐藏语言边界；</li>
 *   <li>包同步的未信任拒绝（断连 + {@code /nekojs trust} 提示）发生在激活门，根本不进入
 *       管线——见 {@code PackSyncClient} 既有语义，本凭证只覆盖“已授权物化文件的准备门”。</li>
 * </ul>
 *
 * <p>凭证不持有源码或 runtime resources；签发时只规范化路径（存在时解析 real path），
 * 不创建 Graal Context、不决定 HostAccess、不读 Minecraft/loader。
 */
public record NekoTrustApprovedSource(Kind kind, String subject, String packId, String keyId) {
    /** 信任来源：本地受信文件，或远端显式授权（带签名 key 证据）。 */
    public enum Kind {
        LOCAL_TRUSTED,
        REMOTE_AUTHORIZED
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
    }

    /** 本地受信文件的凭证（存在时 subject 使用 real path，不存在时使用规范化绝对路径）。 */
    public static NekoTrustApprovedSource local(Path file) {
        return new NekoTrustApprovedSource(Kind.LOCAL_TRUSTED, subjectOf(file), null, null);
    }

    /**
     * 远端显式授权文件的凭证（subject = 物化后文件的 real path；不存在时为规范化绝对路径）。
     *
     * @param packId 授权的包标识（诊断用，不参与覆盖判定）
     * @param keyId  显式授权的签名 key id（必须非空：无 key 证据即无显式授权）
     */
    public static NekoTrustApprovedSource remote(Path file, String packId, String keyId) {
        return new NekoTrustApprovedSource(Kind.REMOTE_AUTHORIZED, subjectOf(file), packId, keyId);
    }

    /** 本凭证是否覆盖该文件（精确路径匹配；包内其它文件需各自的凭证）。 */
    public boolean covers(Path file) {
        return file != null && subject.equals(subjectOf(file));
    }

    /** 诊断用描述（不含密钥材料：keyId 只是标识）。 */
    public String describe() {
        return switch (kind) {
            case LOCAL_TRUSTED -> "local-trusted:" + subject;
            case REMOTE_AUTHORIZED -> "remote-authorized:" + subject
                    + " (pack=" + packId + ", key=" + keyId + ")";
        };
    }

    static String subjectOf(Path file) {
        return NekoCanonicalPath.of(file);
    }

    private static String normalizeSubject(String subject) {
        return subject.replace('\\', '/');
    }

}
