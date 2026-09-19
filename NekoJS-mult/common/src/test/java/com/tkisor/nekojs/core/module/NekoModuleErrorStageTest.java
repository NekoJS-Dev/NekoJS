package com.tkisor.nekojs.core.module;

import com.tkisor.nekojs.core.ScriptFilePolicy;
import com.tkisor.nekojs.core.compiler.NekoCompilationPipeline;
import com.tkisor.nekojs.core.compiler.ScriptCompilerRegistry;
import com.tkisor.nekojs.core.compiler.python.PythonToJsCompiler;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.error.SourceMapRegistry;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.module.esm.NekoEsmDiagnostic;
import com.tkisor.nekojs.core.module.esm.NekoEsmLinkException;
import com.tkisor.nekojs.core.module.esm.NekoEsmVirtualModuleRegistry;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.Source;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 票据 11 AC6：准备失败、resolve/link 失败、缓存失败、执行失败可区分 owner 与阶段；
 * 错误不延迟成无来源的 Graal 异常。
 */
class NekoModuleErrorStageTest {
    @TempDir
    Path gameDir;

    private NekoJSPaths paths;
    private Context context;
    private NekoScriptModuleLoaderHost host;
    private NekoModulePipelineCache cache;

    @BeforeEach
    void setUp() throws Exception {
        TestPlatformInit.ensureInitialized(gameDir);
        paths = pathsFor(gameDir);
        Files.createDirectories(paths.serverScripts().resolve("src"));
        context = Context.newBuilder("js").allowAllAccess(true).build();
        ScriptCompilerRegistry compilers = ScriptCompilerRegistry.createRuntimeRegistry();
        SandboxConfig config = SandboxConfig.defaultConfig();
        cache = new NekoModulePipelineCache(
                new NekoModulePipeline(new NekoCompilationPipeline(), compilers, config),
                new SourceMapRegistry(paths.root()), new NekoEsmVirtualModuleRegistry(paths.root()),
                NekoTrustContext.local());
        host = new NekoScriptModuleLoaderHost(
                context, new NekoModuleResolver(paths.gameDir(), paths.root(), paths.nodeModules(),
                        ScriptFilePolicy.legacyRuntime()), cache);
        context.getBindings("js").putMember("__nekoScriptModuleLoaderHost", host);
        try (var in = getClass().getResourceAsStream("/nekojs/node/internal/script-loader.js")) {
            assertNotNull(in, "script-loader.js must be on the test classpath");
            String loader = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            context.eval(Source.newBuilder("js", loader, "nekojs/node/internal/script-loader.js").build());
        }
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    @Test
    void prepareFailureCarriesPrepareStageAndSourceLocation() {
        ScriptCompilerRegistry compilers = ScriptCompilerRegistry.createRuntimeRegistry();
        compilers.registerLanguage("python", Set.of(".py"), new PythonToJsCompiler());
        NekoModulePipeline pipeline = new NekoModulePipeline(
                new NekoCompilationPipeline(), compilers, SandboxConfig.defaultConfig());

        // 非法 Python（unexpected token）：转译错误自带 file + position。
        Exception failure = assertThrows(Exception.class, () -> pipeline.prepare(
                Path.of("server_scripts/stage-bad.py"), "def broken(:\n  ???\n"));

        NekoModuleError staged = NekoModulePipelinePrepareTest.assertStaged(
                failure, NekoModuleError.Stage.PREPARE, NekoModuleError.OWNER_PREPARATION);
        assertTrue(staged.sourcePath().contains("stage-bad.py"), staged.detail());
        assertTrue(hasNoPolyglotCause(failure), "准备失败不得延迟成无来源的 Graal 异常");
    }

    @Test
    void resolveFailureCarriesResolveStageAndSpecifier() {
        IOException failure = assertThrows(IOException.class,
                () -> host.requireFrom("./server_scripts/src/entry.js", "./missing-xyz.js"));

        NekoModuleError staged = NekoModulePipelinePrepareTest.assertStaged(
                failure, NekoModuleError.Stage.RESOLVE, NekoModuleError.OWNER_RESOLUTION_CACHE);
        assertEquals("./missing-xyz.js", staged.moduleId());
        assertTrue(staged.getMessage().contains("missing-xyz.js"), staged.detail());
    }

    @Test
    void nestedCjsResolutionFailureRetainsResolveStageAndCause() throws Exception {
        Path entry = paths.serverScripts().resolve("src/nested-missing.cjs");
        Files.writeString(entry, "require('./does-not-exist.cjs');\nmodule.exports = 1;\n");

        IOException failure = assertThrows(IOException.class,
                () -> host.loadEntry("./server_scripts/src/nested-missing.cjs"));

        NekoModuleError staged = NekoModulePipelinePrepareTest.assertStaged(
                failure, NekoModuleError.Stage.RESOLVE, NekoModuleError.OWNER_RESOLUTION_CACHE);
        assertEquals("./does-not-exist.cjs", staged.moduleId());
        assertTrue(staged.sourcePath().contains("nested-missing.cjs"), staged.detail());
        assertNotNull(staged.getCause(), "nested resolution failure must retain its original cause");
    }

    @Test
    void linkFailureCarriesFileLineAndColumn() throws Exception {
        Path dep = paths.serverScripts().resolve("src/link-dep.mjs");
        Path entry = paths.serverScripts().resolve("src/link-entry.mjs");
        Files.writeString(dep, "export const real = 1;\n");
        Files.writeString(entry, "import { ghost } from './link-dep.mjs';\n"
                + "export const value = ghost;\n");

        IOException failure = assertThrows(IOException.class,
                () -> host.loadEntry("./server_scripts/src/link-entry.mjs"));

        NekoModuleError staged = NekoModulePipelinePrepareTest.assertStaged(
                failure, NekoModuleError.Stage.LINK, NekoModuleError.OWNER_RESOLUTION_CACHE);
        NekoEsmLinkException link = assertInstanceOf(NekoEsmLinkException.class, staged.getCause());
        NekoEsmDiagnostic diagnostic = link.diagnostic();
        assertNotNull(diagnostic.file(), "link 失败必须携带源文件");
        assertTrue(diagnostic.file().toString().replace('\\', '/').endsWith("link-entry.mjs"),
                "link 失败必须指向引用方， was: " + diagnostic.file());
        assertTrue(diagnostic.line() > 0, "link 失败必须保留源行: " + diagnostic);
        assertTrue(diagnostic.column() > 0, "link 失败必须保留源列: " + diagnostic);
        assertTrue(diagnostic.message().contains("ghost"), "link 失败必须点名缺失导出: " + diagnostic);
    }

    @Test
    void cacheFailureCarriesCacheStageAndPath() {
        Path missing = gameDir.resolve("nekojs/server_scripts/src/no-such-file.cjs");

        IOException failure = assertThrows(IOException.class, () -> cache.prepare(missing));

        NekoModuleError staged = NekoModulePipelinePrepareTest.assertStaged(
                failure, NekoModuleError.Stage.CACHE, NekoModuleError.OWNER_RESOLUTION_CACHE);
        assertTrue(staged.sourcePath().contains("no-such-file.cjs"), staged.detail());
    }

    @Test
    void jsonReadFailureCarriesCacheStageAndPath() {
        Path missing = gameDir.resolve("nekojs/server_scripts/src/no-such-data.json");

        IOException failure = assertThrows(IOException.class, () -> cache.prepareJson(missing));

        NekoModuleError staged = NekoModulePipelinePrepareTest.assertStaged(
                failure, NekoModuleError.Stage.CACHE, NekoModuleError.OWNER_RESOLUTION_CACHE);
        assertTrue(staged.sourcePath().contains("no-such-data.json"), staged.detail());
        assertNotNull(staged.getCause(), "JSON read failure must retain its I/O cause");
    }

    @Test
    void executeFailureCarriesExecuteStageAndModule() throws Exception {
        // 未配置 executor/factory 的裸 host：装载失败归执行环境，不伪装成解析失败。
        try (Context bare = Context.newBuilder("js").allowAllAccess(true).build()) {
            NekoScriptModuleLoaderHost bareHost = new NekoScriptModuleLoaderHost(
                    bare, new NekoModuleResolver(paths.gameDir(), paths.root(), paths.nodeModules(),
                            ScriptFilePolicy.legacyRuntime()), cache);
            Path entry = paths.serverScripts().resolve("src/exec-entry.cjs");
            Files.writeString(entry, "module.exports = 1;\n");

            IOException failure = assertThrows(IOException.class,
                    () -> bareHost.loadEntry("./server_scripts/src/exec-entry.cjs"));

            NekoModuleError staged = NekoModulePipelinePrepareTest.assertStaged(
                    failure, NekoModuleError.Stage.EXECUTE, NekoModuleError.OWNER_EXECUTION);
            assertTrue(staged.moduleId().contains("exec-entry.cjs"), staged.detail());
        }
    }

    @Test
    void guestRuntimeErrorCarriesExecuteStageAndOriginalCause() throws Exception {
        Path entry = paths.serverScripts().resolve("src/guest-boom.cjs");
        Files.writeString(entry, "throw new Error('guest-boom');\n");

        IOException failure = assertThrows(IOException.class,
                () -> host.loadEntry("./server_scripts/src/guest-boom.cjs"));
        NekoModuleError staged = NekoModulePipelinePrepareTest.assertStaged(
                failure, NekoModuleError.Stage.EXECUTE, NekoModuleError.OWNER_EXECUTION);
        assertTrue(String.valueOf(staged.getMessage()).contains("guest-boom"), String.valueOf(staged));
        assertNotNull(staged.getCause(), "guest error must remain available as the cause");
    }

    private static boolean hasNoPolyglotCause(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current.getClass().getName().contains("Polyglot")) {
                return false;
            }
        }
        return true;
    }

    private static NekoJSPaths pathsFor(Path gameDir) throws Exception {
        Constructor<NekoJSPaths> constructor = NekoJSPaths.class.getDeclaredConstructor(Path.class);
        constructor.setAccessible(true);
        return constructor.newInstance(gameDir);
    }
}
