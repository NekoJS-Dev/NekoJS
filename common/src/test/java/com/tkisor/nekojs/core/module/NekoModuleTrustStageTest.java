package com.tkisor.nekojs.core.module;

import com.tkisor.nekojs.core.compiler.NekoCompilationPipeline;
import com.tkisor.nekojs.core.compiler.NekoModuleMode;
import com.tkisor.nekojs.core.compiler.ScriptCompilerRegistry;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Method;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 票据 11 AC8：本地 trusted 与远端 explicitly-authorized source 复用同一
 * prepare/resolve/execute 阶段，只让 trust 决策影响授权结果。
 */
class NekoModuleTrustStageTest {
    @TempDir
    Path gameDir;

    @BeforeAll
    static void bindPlatform() {
        TestPlatformInit.ensureInitialized();
    }

    private static NekoModulePipeline pipeline() {
        return new NekoModulePipeline(new NekoCompilationPipeline(),
                ScriptCompilerRegistry.createRuntimeRegistry(), SandboxConfig.defaultConfig());
    }

    @Test
    void localAndRemoteApprovalShareIdenticalPrepareStages() throws Exception {
        NekoModulePipeline pipeline = pipeline();
        Path file = gameDir.resolve("nekojs/server_scripts/trust-same.cjs");
        String source = "module.exports = 'trust-shape';\n";

        NekoPreparedModule viaLocal = pipeline.prepare(file, source, NekoTrustApprovedSource.local(file));
        NekoPreparedModule viaRemote = pipeline.prepare(file, source,
                NekoTrustApprovedSource.remote(file, "packs:demo", "author-key"));

        assertEquals(viaLocal.code(), viaRemote.code(), "trust kind 不得改变准备产物");
        assertEquals(viaLocal.mode(), viaRemote.mode());
        assertEquals(NekoModuleMode.COMMONJS, viaRemote.mode());
        assertEquals("javascript", viaRemote.languageId());
        assertEquals(viaLocal.cacheKey(), viaRemote.cacheKey(), "同源同内容 key 一致");
        assertEquals(viaLocal.sourceMap(), viaRemote.sourceMap(), "source map 一致");
    }

    @Test
    void missingOrMismatchedCredentialIsDeniedWithLanguageBoundary() {
        NekoModulePipeline pipeline = pipeline();
        Path file = gameDir.resolve("nekojs/server_scripts/trust-denied.cjs");
        String source = "module.exports = 1;\n";

        // 缺凭证。
        Exception missing = assertThrows(Exception.class, () -> pipeline.prepare(file, source, null));
        NekoModuleError deniedMissing = NekoModulePipelinePrepareTest.assertStaged(
                missing, NekoModuleError.Stage.PREPARE, NekoModuleError.OWNER_PACK_TRUST);
        assertTrue(deniedMissing.sourcePath().contains("trust-denied.cjs"), deniedMissing.detail());
        assertTrue(deniedMissing.getMessage().contains("javascript"), "拒绝不得隐藏语言边界: " + deniedMissing.detail());
        assertTrue(deniedMissing.getMessage().contains("COMMONJS"), deniedMissing.detail());

        // 错配凭证（其它文件的凭证）。
        Path other = gameDir.resolve("nekojs/server_scripts/trust-other.cjs");
        Exception mismatched = assertThrows(Exception.class,
                () -> pipeline.prepare(file, source, NekoTrustApprovedSource.local(other)));
        NekoModuleError deniedMismatch = NekoModulePipelinePrepareTest.assertStaged(
                mismatched, NekoModuleError.Stage.PREPARE, NekoModuleError.OWNER_PACK_TRUST);
        assertTrue(deniedMismatch.sourcePath().contains("trust-denied.cjs"), deniedMismatch.detail());
    }

    @Test
    void remoteApprovalRequiresExplicitKeyEvidence() {
        Path file = gameDir.resolve("nekojs/server_scripts/trust-key.cjs");

        assertThrows(IllegalArgumentException.class,
                () -> NekoTrustApprovedSource.remote(file, "packs:demo", null),
                "远端授权无 key 证据即无显式授权，必须拒绝签发");
        assertThrows(IllegalArgumentException.class,
                () -> NekoTrustApprovedSource.remote(file, "packs:demo", "  "));

        NekoTrustApprovedSource local = NekoTrustApprovedSource.local(file);
        assertTrue(local.covers(file));
        assertFalse(local.covers(file.resolveSibling("trust-sibling.cjs")), "凭证精确绑定单文件");
        assertEquals(NekoTrustApprovedSource.Kind.LOCAL_TRUSTED, local.kind());
    }

    @Test
    void trustDecisionOnlyLivesAtPrepareGate() {
        // 结构证据：resolve/execute 路径不接受信任凭证——trust 只影响准备门的授权结果，
        // 解析与执行阶段对本地/远端一视同仁。
        for (Class<?> seam : new Class<?>[]{
                NekoScriptModuleLoaderHost.class, NekoModuleResolver.class, NekoModulePipelineCache.class}) {
            for (Method method : seam.getDeclaredMethods()) {
                for (Class<?> parameter : method.getParameterTypes()) {
                    assertFalse(parameter == NekoTrustApprovedSource.class,
                            seam.getSimpleName() + "#" + method.getName() + " must not take a trust credential");
                }
            }
        }
    }
}
