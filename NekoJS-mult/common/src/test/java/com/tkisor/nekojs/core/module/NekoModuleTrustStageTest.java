package com.tkisor.nekojs.core.module;

import com.tkisor.nekojs.core.compiler.NekoCompilationPipeline;
import com.tkisor.nekojs.core.compiler.NekoModuleMode;
import com.tkisor.nekojs.core.compiler.ScriptCompilerRegistry;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.ScriptFilePolicy;
import com.tkisor.nekojs.core.fs.NekoJSFileSystem;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.fs.SandboxPolicy;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.Source;
import graal.graalvm.polyglot.Value;
import graal.graalvm.polyglot.io.IOAccess;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

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
    void trustCoverageUsesCanonicalPathAndWindowsCaseRules() throws Exception {
        Path file = gameDir.resolve("nekojs/server_scripts/trust-canonical.cjs");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "module.exports = 1;\n");
        try {
            NekoTrustApprovedSource approval = NekoTrustApprovedSource.local(file);
            Path alias = file.getParent().resolve(".").resolve(file.getFileName());
            assertTrue(approval.covers(alias), "dot-segment aliases resolve to the same real file");
            if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
                assertTrue(approval.covers(Path.of(file.toString().toUpperCase(Locale.ROOT))),
                        "Windows file identity must ignore path casing");
            }
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    void moduleHostRequiresAnApprovalForEveryResolvedDependency() throws Exception {
        TestPlatformInit.ensureInitialized(gameDir);
        NekoJSPaths paths = NekoJSPaths.fromGameDir(gameDir);
        Path entry = paths.serverScripts().resolve("trusted-entry.mjs");
        Path dependency = paths.serverScripts().resolve("untrusted-dependency.mjs");
        Files.createDirectories(entry.getParent());
        Files.writeString(entry, "import { value } from './untrusted-dependency.mjs';\nexport { value };\n");
        Files.writeString(dependency, "export const value = 42;\n");
        Path trustedEntry = entry.toRealPath();

        NekoModulePipelineCache cache = new NekoModulePipelineCache(
                pipeline(), path -> path.equals(trustedEntry) ? NekoTrustApprovedSource.local(path) : null);
        try (Context context = Context.newBuilder("js").allowAllAccess(true).build()) {
            NekoScriptModuleLoaderHost host = new NekoScriptModuleLoaderHost(context,
                    new NekoModuleResolver(paths, ScriptFilePolicy.legacyRuntime()), paths, cache);
            context.getBindings("js").putMember("__nekoScriptModuleLoaderHost", host);
            try (var input = getClass().getResourceAsStream("/nekojs/node/internal/script-loader.js")) {
                assertTrue(input != null, "script loader resource must exist");
                context.eval(Source.newBuilder("js", new String(input.readAllBytes(), StandardCharsets.UTF_8),
                        "nekojs/node/internal/script-loader.js").build());
            }

            Exception failure = assertThrows(Exception.class,
                    () -> host.loadEntry("./server_scripts/trusted-entry.mjs"));
            NekoModuleError denied = NekoModulePipelinePrepareTest.assertStaged(
                    failure, NekoModuleError.Stage.PREPARE, NekoModuleError.OWNER_PACK_TRUST);
            assertTrue(denied.sourcePath().endsWith("untrusted-dependency.mjs"), denied.detail());
        } finally {
            cache.clear();
            Files.deleteIfExists(entry);
            Files.deleteIfExists(dependency);
        }
    }

    @Test
    void remoteApprovalFlowsThroughHostCacheAndJsonPreparation() throws Exception {
        TestPlatformInit.ensureInitialized(gameDir);
        NekoJSPaths paths = NekoJSPaths.fromGameDir(gameDir);
        Path cjsEntry = paths.serverScripts().resolve("remote-entry.cjs");
        Path esmEntry = paths.serverScripts().resolve("remote-entry.mjs");
        Path json = paths.serverScripts().resolve("remote-data.json");
        Path deniedEntry = paths.serverScripts().resolve("remote-denied-entry.cjs");
        Path deniedJson = paths.serverScripts().resolve("remote-denied.json");
        Files.createDirectories(cjsEntry.getParent());
        Files.writeString(cjsEntry, "module.exports = require('./remote-data.json');\n");
        Files.writeString(esmEntry, "import data from './remote-data.json';\nexport const value = data.value;\n");
        Files.writeString(json, "{\"value\":73}\n");
        Files.writeString(deniedEntry, "module.exports = require('./remote-denied.json');\n");
        Files.writeString(deniedJson, "{\"value\":99}\n");

        var authorized = java.util.Set.of(cjsEntry.toRealPath(), esmEntry.toRealPath(), json.toRealPath(),
                deniedEntry.toRealPath());
        NekoTrustContext remote = path -> authorized.contains(path.toAbsolutePath().normalize())
                ? NekoTrustApprovedSource.remote(path, "packs:remote", "author-key") : null;
        NekoModulePipelineCache cache = new NekoModulePipelineCache(pipeline(),
                new com.tkisor.nekojs.core.error.SourceMapRegistry(paths.root()),
                new com.tkisor.nekojs.core.module.esm.NekoEsmVirtualModuleRegistry(paths.root()), remote);
        IOAccess ioAccess = IOAccess.newBuilder()
                .fileSystem(new NekoJSFileSystem(paths.root(), new SandboxPolicy(SandboxConfig.defaultConfig(), paths), cache))
                .build();
        try (Context context = Context.newBuilder("js").allowAllAccess(true).allowIO(ioAccess).build()) {
            NekoScriptModuleLoaderHost host = new NekoScriptModuleLoaderHost(context,
                    new NekoModuleResolver(paths, ScriptFilePolicy.legacyRuntime()), paths, cache);
            context.getBindings("js").putMember("__nekoScriptModuleLoaderHost", host);
            try (var input = getClass().getResourceAsStream("/nekojs/node/internal/script-loader.js")) {
                assertTrue(input != null, "script loader resource must exist");
                context.eval(Source.newBuilder("js", new String(input.readAllBytes(), StandardCharsets.UTF_8),
                        "nekojs/node/internal/script-loader.js").build());
            }

            Value cjs = (Value) host.loadEntry("./server_scripts/remote-entry.cjs");
            assertEquals(73, cjs.getMember("value").asInt());
            Value esm = (Value) host.loadEntry("./server_scripts/remote-entry.mjs");
            assertEquals(73, esm.getMember("value").asInt());

            Exception failure = assertThrows(Exception.class,
                    () -> host.loadEntry("./server_scripts/remote-denied-entry.cjs"));
            NekoModuleError denied = NekoModulePipelinePrepareTest.assertStaged(
                    failure, NekoModuleError.Stage.PREPARE, NekoModuleError.OWNER_PACK_TRUST);
            assertTrue(denied.sourcePath().endsWith("remote-denied.json"), denied.detail());
        } finally {
            cache.clear();
            Files.deleteIfExists(cjsEntry);
            Files.deleteIfExists(esmEntry);
            Files.deleteIfExists(json);
            Files.deleteIfExists(deniedEntry);
            Files.deleteIfExists(deniedJson);
        }
    }
}
