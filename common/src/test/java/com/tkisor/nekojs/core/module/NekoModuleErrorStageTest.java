package com.tkisor.nekojs.core.module;

import com.tkisor.nekojs.core.ScriptFilePolicy;
import com.tkisor.nekojs.core.compiler.NekoCompilationPipeline;
import com.tkisor.nekojs.core.compiler.ScriptCompilerRegistry;
import com.tkisor.nekojs.core.compiler.python.PythonToJsCompiler;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.module.esm.NekoEsmDiagnostic;
import com.tkisor.nekojs.core.module.esm.NekoEsmLinkException;
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
        cache = NekoModulePipelineCache.withExplicitPipeline(
                ScriptCompilerRegistry.createRuntimeRegistry(), SandboxConfig.defaultConfig());
        host = new NekoScriptModuleLoaderHost(
                context, new NekoModuleResolver(paths, ScriptFilePolicy.legacyRuntime()), paths, cache);
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
    void linkFailureCarriesFileLineAndColumn() throws Exception {
        Path dep = paths.serverScripts().resolve("src/link-dep.mjs");
        Path entry = paths.serverScripts().resolve("src/link-entry.mjs");
        Files.writeString(dep, "export const real = 1;\n");
        Files.writeString(entry, "import { ghost } from './link-dep.mjs';\n"
                + "export const value = ghost;\n");

        IOException failure = assertThrows(IOException.class,
                () -> host.loadEntry("./server_scripts/src/link-entry.mjs"));

        NekoEsmLinkException link = assertInstanceOf(NekoEsmLinkException.class, failure);
        NekoEsmDiagnostic diagnostic = link.diagnostic();
        assertNotNull(diagnostic.file(), "link 失败必须携带源文件");
        assertTrue(diagnostic.file().toString().replace('\\', '/').endsWith("link-entry.mjs"),
                "link 失败必须指向引用方， was: " + diagnostic.file());
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
    void executeFailureCarriesExecuteStageAndModule() throws Exception {
        // 未配置 executor/factory 的裸 host：装载失败归执行环境，不伪装成解析失败。
        try (Context bare = Context.newBuilder("js").allowAllAccess(true).build()) {
            NekoScriptModuleLoaderHost bareHost = new NekoScriptModuleLoaderHost(
                    bare, new NekoModuleResolver(paths, ScriptFilePolicy.legacyRuntime()), paths, cache);
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
    void guestRuntimeErrorIsNotWrappedAsModuleError() throws Exception {
        Path entry = paths.serverScripts().resolve("src/guest-boom.cjs");
        Files.writeString(entry, "throw new Error('guest-boom');\n");

        // guest 运行时异常原样传播（调用者靠“非 NekoModuleError”识别执行期语义错误）。
        RuntimeException failure = assertThrows(RuntimeException.class,
                () -> host.loadEntry("./server_scripts/src/guest-boom.cjs"));
        assertTrue(String.valueOf(failure.getMessage()).contains("guest-boom"), String.valueOf(failure));
        for (Throwable current = failure; current != null; current = current.getCause()) {
            assertTrue(!(current instanceof NekoModuleError),
                    "guest 异常不得被包成阶段错误: " + current);
        }
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
