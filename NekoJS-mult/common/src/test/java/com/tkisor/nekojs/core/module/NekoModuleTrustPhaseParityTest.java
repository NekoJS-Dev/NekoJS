package com.tkisor.nekojs.core.module;

import com.tkisor.nekojs.core.compiler.NekoCompilationPipeline;
import com.tkisor.nekojs.core.compiler.NekoModuleMode;
import com.tkisor.nekojs.core.compiler.ScriptCompilerRegistry;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.pack.sync.PackContentFile;
import com.tkisor.nekojs.core.pack.sync.PackSignatureVerifier;
import com.tkisor.nekojs.core.pack.sync.PackSyncTrustStore;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 工单 11 AC8：本地 trusted 与远端 explicitly-authorized source 走<strong>同一套可追踪阶段</strong>，
 * trust 决策只影响授权结果，不隐藏语言与模块边界。
 *
 * <p>证据分两层：
 * <ol>
 *   <li><strong>阶段同一性</strong>：本地脚本与「远端授权包」里的同名内容经同一
 *       {@link NekoModulePreparation#prepare} 得到相同 language id / module mode /
 *       稳定 cache key（路径不同，故 key 不同；语言与模块语义必须一致）；</li>
 *   <li><strong>授权结果独立</strong>：{@link PackSignatureVerifier} 的拒绝 / 未签名降级
 *       只改变 {@code valid}/{@code reason}，不改变源如何被准备——即拒绝不伪装成
 *       Preparation 或 Resolution 失败，语言边界对调用方始终可见。</li>
 * </ol>
 *
 * <p>信任存储与签名密钥都是硬依赖（Ed25519），本测试直接用真实
 * {@link PackSyncTrustStore}（临时文件）与真实 {@code PackSignatureVerifier}，
 * 不做 mock。
 */
class NekoModuleTrustPhaseParityTest {

    @TempDir
    Path trustDir;

    @BeforeAll
    static void initPlatform() {
        TestPlatformInit.ensureInitialized();
    }

    private static NekoModulePreparation preparation() {
        NekoJSPaths paths = NekoJSPaths.get();
        return new NekoModulePreparation(new NekoModulePipeline(
                new NekoCompilationPipeline(), ScriptCompilerRegistry.createRuntimeRegistry(),
                SandboxConfig.defaultConfig()), paths);
    }

    private static Path write(String relative, String source) throws IOException {
        Path file = NekoJSPaths.get().root().resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, source);
        file.toFile().deleteOnExit();
        return file;
    }

    // ---- 阶段同一性：同一 prepare 阶段处理本地与远端授权源 ----

    @Test
    void localAndRemoteAuthorizedSourceShareTheSamePreparationStage() throws Exception {
        String source = "export const value = 41 + 1;\n";
        Path local = write("test_scripts/trust-local.mjs", source);
        // 远端授权包落盘后的路径（nekojs/server_packs/<bucket>/<pack>/...）
        Path remote = write("server_packs/trustbucket/packs_demo/test_scripts/trust-remote.mjs", source);

        NekoModulePreparation preparation = preparation();
        NekoPreparedModule localPrepared = preparation.prepare(local);
        NekoPreparedModule remotePrepared = preparation.prepare(remote);

        // 语言与模块边界完全一致：trust 决策不改变语言管线
        assertEquals(localPrepared.languageId(), remotePrepared.languageId(),
                "本地与远端授权源必须解析到同一 language id");
        assertEquals(NekoModuleMode.ESM, localPrepared.mode());
        assertEquals(NekoModuleMode.ESM, remotePrepared.mode(),
                "本地与远端授权源必须解析到同一 module mode");
        assertEquals(localPrepared.requestedMode(), remotePrepared.requestedMode());
        assertNotNull(localPrepared.esmAst());
        assertNotNull(remotePrepared.esmAst(), "远端授权源必须得到同样的 ESM AST 处理");
        // 路径不同 → key 不同；但内容指纹必须相同（同一份源）
        assertEquals(localPrepared.cacheKey().contentHash(), remotePrepared.cacheKey().contentHash(),
                "同一份源内容必须产生相同内容指纹");
        assertFalse(localPrepared.cacheKey().equals(remotePrepared.cacheKey()),
                "不同路径必须是不同 cache 条目（身份由路径参与）");
    }

    @Test
    void remoteSourceRejectedByTrustStillPreparesIdenticallyWhenInvoked() throws Exception {
        // 拒绝发生在 Pack Trust（授权阶段）；语言/模块边界与本地完全一致。
        // 本测试把「拒绝」建模为：trust 判定的结果不参与 prepare 的输入。
        String source = "module.exports = { ok: true };\n";
        Path local = write("test_scripts/trust-cjs-local.cjs", source);
        Path remote = write("server_packs/trustbucket/packs_demo/test_scripts/trust-cjs-remote.cjs", source);

        NekoModulePreparation preparation = preparation();
        NekoPreparedModule localPrepared = preparation.prepare(local);
        NekoPreparedModule remotePrepared = preparation.prepare(remote);

        assertEquals("javascript", localPrepared.languageId());
        assertEquals("javascript", remotePrepared.languageId(), "拒绝不得改变远端源的语言身份");
        assertEquals(NekoModuleMode.COMMONJS, remotePrepared.mode(), "拒绝不得改变远端源的 module mode");
        assertNotNull(remotePrepared.cjsRecord(), "拒绝不得跳过静态分析（语言边界不隐藏）");
    }

    // ---- 授权结果独立：拒绝/降级只影响 valid/reason ----

    @Test
    void signatureRejectionIsReportedAsAuthorizationResultNotLanguageFailure() throws Exception {
        PackSyncTrustStore store = new PackSyncTrustStore(trustDir.resolve("trusted-servers.json"));
        String manifest = "{\"id\": \"demo\", \"signature\": {\"algorithm\": \"Ed25519\", \"keyId\": \"kid1\", \"publicKey\": \"not-base64!!\", \"signature\": \"not-base64!!\"}}";

        PackSignatureVerifier.Result result = PackSignatureVerifier.verify(
                "packs:demo", "packs", manifest,
                List.of(new PackContentFile("test_scripts/a.js", "x".getBytes(StandardCharsets.UTF_8))),
                false, store);

        assertFalse(result.valid(), "无效签名必须被拒绝");
        assertTrue(result.signed(), "携带 signature 块的包必须标记为 signed");
        assertNotNull(result.reason(), "拒绝必须给出可解释的 reason（授权结果，非语言失败）");
    }

    @Test
    void unsignedPackIsRejectedByDefaultAndAcceptedOnlyWhenExplicitlyAllowed() {
        PackSyncTrustStore store = new PackSyncTrustStore(trustDir.resolve("trusted-servers.json"));
        String manifest = "{\"id\": \"demo\"}";

        PackSignatureVerifier.Result rejected = PackSignatureVerifier.verify(
                "packs:demo", "packs", manifest, List.of(), false, store);
        assertFalse(rejected.valid(), "默认（allowUnsigned=false）必须拒绝未签名包");
        assertFalse(rejected.signed(), "未签名包必须显式标记 signed=false");
        assertTrue(rejected.reason().contains("allowUnsigned"), "拒绝原因必须点明 allowUnsigned：" + rejected.reason());

        PackSignatureVerifier.Result accepted = PackSignatureVerifier.verify(
                "packs:demo", "packs", manifest, List.of(), true, store);
        assertTrue(accepted.valid(), "显式授权（allowUnsigned=true）的未签名包必须放行");
        assertFalse(accepted.signed(), "放行不改变 signed=false 的事实（降级必须可见）");
    }

    /**
     * 降级可见性：显式授权未签名包时，{@code signed=false} 仍然暴露给调用方——
     * 授权结果与语言/模块边界是两条正交信息，不互相掩盖。
     */
    @Test
    void explicitlyAuthorizedUnsignedPackDoesNotHideItsUnverifiedNature() {
        PackSyncTrustStore store = new PackSyncTrustStore(trustDir.resolve("trusted-servers.json"));
        PackSignatureVerifier.Result result = PackSignatureVerifier.verify(
                "packs:demo", "packs", "{}", List.of(), true, store);

        assertTrue(result.valid());
        assertFalse(result.signed(), "降级（未验签放行）必须保持可见，不得伪装成已验签");
        assertNotNull(result.reason(), "授权决定必须附带 reason 供审计");
    }
}
