package com.tkisor.nekojs.core.module;

import com.tkisor.nekojs.core.ScriptFilePolicy;
import com.tkisor.nekojs.core.compiler.NekoCompilationPipeline;
import com.tkisor.nekojs.core.compiler.ScriptCompilerRegistry;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.error.SourceMapRegistry;
import com.tkisor.nekojs.core.fs.NekoJSFileSystem;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.fs.SandboxPolicy;
import com.tkisor.nekojs.core.module.NekoEsmVirtualModuleRegistry;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.Source;
import graal.graalvm.polyglot.Value;
import graal.graalvm.polyglot.io.IOAccess;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Smoke test for the minimal runnable JS/CJS/ESM examples that ship with the module pipeline.
 *
 * <p>The example sources live in {@code common/src/test/resources/nekojs/module-examples/} (the
 * same content is inlined in the baseline migration material). The test copies them into an
 * isolated gameDir, loads them through the public entry points and asserts that the output and
 * the cache and reload behavior match what the docs promise. The examples only use module
 * capabilities that already pass the gate: require and module.exports, import and export,
 * invalidation by path plus re-running the entry.
 */
class ModuleExamplesSmokeTest {
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
        ScriptCompilerRegistry compilers = ScriptCompilerRegistry.createRuntimeRegistry();
        SandboxConfig config = SandboxConfig.defaultConfig();
        cache = new NekoModulePipelineCache(
                new NekoModulePipeline(new NekoCompilationPipeline(), compilers, config),
                new SourceMapRegistry(paths.root()), new NekoEsmVirtualModuleRegistry(paths.root()),
                NekoTrustContext.local());
        IOAccess ioAccess = IOAccess.newBuilder()
                .fileSystem(new NekoJSFileSystem(paths.root(),
                        new SandboxPolicy(SandboxConfig.defaultConfig(), paths), paths, cache))
                .build();
        context = Context.newBuilder("js").allowAllAccess(true).allowIO(ioAccess).build();
        host = new NekoScriptModuleLoaderHost(
                context, new NekoModuleResolver(paths.gameDir(), paths.root(), paths.nodeModules(),
                        ScriptFilePolicy.legacyRuntime()), cache);
        context.getBindings("js").putMember("__nekoScriptModuleLoaderHost", host);
        try (var in = getClass().getResourceAsStream("/nekojs/node/internal/script-loader.js")) {
            assertNotNull(in, "script-loader.js must be on the test classpath");
            String loader = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            context.eval(Source.newBuilder("js", loader, "nekojs/node/internal/script-loader.js").build());
        }
        installExamples();
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    @Test
    void jsExampleRunsWithDocumentedOutput() throws Exception {
        Value exports = asValue(host.loadEntry("./server_scripts/examples/js/hello.js"));

        assertEquals("hello, neko!", exports.getMember("message").asString());
    }

    @Test
    void cjsExampleRunsWithDocumentedOutput() throws Exception {
        Value exports = asValue(host.loadEntry("./server_scripts/examples/cjs/hello.cjs"));

        assertEquals(42, exports.getMember("sum").asInt());
        assertTrue(exports.getMember("identical").asBoolean(), "重复 require 返回同一模块身份");
    }

    @Test
    void esmExampleRunsWithDocumentedOutput() throws Exception {
        Value namespace = asValue(host.loadEntry("./server_scripts/examples/esm/hello.mjs"));

        assertEquals("hi, neko!", namespace.getMember("message").asString());
        assertEquals("esm", namespace.getMember("tag").asString());
        assertTrue(namespace.getMember("same").asBoolean(), "命名空间默认导出身份一致");
    }

    @Test
    void documentedCacheAndReloadBehaviorHolds() throws Exception {
        Path hello = paths.serverScripts().resolve("examples/js/hello.js");
        Path greet = paths.serverScripts().resolve("examples/js/greet.js");

        // Documented promise 1: preparing the same content again hits the cache with a stable key.
        NekoPreparedModule first = cache.prepare(hello);
        NekoPreparedModule second = cache.prepare(hello);
        assertEquals(first.cacheKey(), second.cacheKey());

        Value before = asValue(host.loadEntry("./server_scripts/examples/js/hello.js"));
        assertEquals("hello, neko!", before.getMember("message").asString());

        // Documented promise 2: after the content changes, invalidating and re-running the entry shows the
        // new output, and the old module does not come back.
        Files.writeString(greet, Files.readString(greet).replace("hello, ", "hey, "));
        host.invalidateModuleTree("./server_scripts/examples/js/hello.js");

        Value after = asValue(host.loadEntry("./server_scripts/examples/js/hello.js"));
        assertEquals("hey, neko!", after.getMember("message").asString());
    }

    private void installExamples() throws Exception {
        copyExample("js", "hello.js");
        copyExample("js", "greet.js");
        copyExample("cjs", "hello.cjs");
        copyExample("cjs", "greet.cjs");
        copyExample("esm", "hello.mjs");
        copyExample("esm", "greet.mjs");
    }

    private void copyExample(String flavor, String file) throws Exception {
        String resource = "/nekojs/module-examples/" + flavor + "/" + file;
        try (var in = getClass().getResourceAsStream(resource)) {
            assertNotNull(in, "example must exist: " + resource);
            String source = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            Path target = paths.serverScripts().resolve("examples/" + flavor + "/" + file);
            Files.createDirectories(target.getParent());
            Files.writeString(target, source);
        }
    }

    private static Value asValue(Object exports) {
        assertTrue(exports instanceof Value, "host 应返回 guest Value, was: " + exports);
        return (Value) exports;
    }

    private static NekoJSPaths pathsFor(Path gameDir) throws Exception {
        Constructor<NekoJSPaths> constructor = NekoJSPaths.class.getDeclaredConstructor(Path.class);
        constructor.setAccessible(true);
        return constructor.newInstance(gameDir);
    }
}
