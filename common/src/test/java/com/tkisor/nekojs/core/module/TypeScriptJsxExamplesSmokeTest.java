package com.tkisor.nekojs.core.module;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.core.ScriptFilePolicy;
import com.tkisor.nekojs.core.compiler.NekoCompilationPipeline;
import com.tkisor.nekojs.core.compiler.NekoJsxLanguagePlugin;
import com.tkisor.nekojs.core.compiler.NekoTsxLanguagePlugin;
import com.tkisor.nekojs.core.compiler.NekoTypeScriptLanguagePlugin;
import com.tkisor.nekojs.core.compiler.ScriptCompilerRegistry;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.error.DefaultErrorTracker;
import com.tkisor.nekojs.core.error.SourceMapRegistry;
import com.tkisor.nekojs.core.fs.NekoJSFileSystem;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.fs.SandboxPolicy;
import com.tkisor.nekojs.core.node.NekoNodeModuleInstaller;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import graal.graalvm.polyglot.Context;
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

/**
 * 票据 12 AC9：随票交付的 .ts / .jsx / .tsx 最小可运行示例冒烟。
 *
 * <p>示例源文件在 {@code common/src/test/resources/nekojs/language-ts-examples/}（交付物，
 * 内容同时内联于基线迁移材料）。测试把它们拷入隔离 gameDir 后经最高调用者 seam
 * （{@link NekoScriptModuleLoaderHost#loadEntry}）真实装载，断言文档承诺的输出与
 * source-map 行为一致。示例只使用已过 gate 的语言能力：类型擦除、classic JSX
 * element/fragment、TSX 组合与 ESM import。
 */
class TypeScriptJsxExamplesSmokeTest {

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
        Files.createDirectories(paths.serverScripts().resolve("examples"));
        ScriptCompilerRegistry compilers = ScriptCompilerRegistry.createRuntimeRegistry();
        compilers.register(NekoTypeScriptLanguagePlugin.INSTANCE);
        compilers.register(NekoJsxLanguagePlugin.INSTANCE);
        compilers.register(NekoTsxLanguagePlugin.INSTANCE);
        SandboxConfig config = SandboxConfig.defaultConfig();
        cache = new NekoModulePipelineCache(
                new NekoModulePipeline(new NekoCompilationPipeline(), compilers, config),
                new SourceMapRegistry(paths.root()), new NekoEsmVirtualModuleRegistry(paths.root()),
                NekoTrustContext.local());
        IOAccess ioAccess = IOAccess.newBuilder()
                .fileSystem(new NekoJSFileSystem(paths.root(), new SandboxPolicy(config, paths), paths, cache))
                .build();
        context = Context.newBuilder("js").allowAllAccess(true).allowIO(ioAccess).build();
        NekoModuleResolver resolver = new NekoModuleResolver(paths.gameDir(), paths.root(), paths.nodeModules(),
                new ScriptFilePolicy(compilers));
        NekoNodeModuleInstaller.install(context, ScriptType.TEST, resolver, paths,
                new DefaultErrorTracker(paths, config), config, cache);
        host = (NekoScriptModuleLoaderHost) context.getBindings("js")
                .getMember("__nekoScriptModuleLoaderHost").asHostObject();
        installExamples();
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    @Test
    void tsExampleRunsWithDocumentedOutput() throws Exception {
        Value exports = asValue(host.loadEntry("./server_scripts/examples/ts/hello.ts"));

        assertEquals("hello, neko!", exports.getMember("message").asString());
        assertEquals("ts", exports.getMember("tag").asString());
        assertEquals(42, exports.getMember("answer").asInt(),
                "erased type annotations and generics must not change runtime values");
    }

    @Test
    void jsxExampleRunsWithDocumentedOutput() throws Exception {
        Value exports = asValue(host.loadEntry("./server_scripts/examples/jsx/hello.jsx"));

        assertEquals("div", exports.getMember("rootTag").asString());
        assertEquals("root", exports.getMember("rootId").asString());
        assertEquals("span", exports.getMember("childTag").asString());
        assertEquals("jsx", exports.getMember("childText").asString());
        assertEquals(0, exports.getMember("fragmentChildren").asInt(),
                "an empty fragment must lower to a fragment factory call with no children");
    }

    @Test
    void tsxExampleRunsWithDocumentedOutput() throws Exception {
        Value exports = asValue(host.loadEntry("./server_scripts/examples/tsx/hello.tsx"));

        assertEquals("list", exports.getMember("listId").asString());
        assertEquals(2, exports.getMember("childCount").asInt());
        assertEquals("tsx", exports.getMember("labelText").asString());
    }

    @Test
    void documentedSourceMapAndLanguageIdentityHold() throws Exception {
        for (String[] entry : new String[][]{
                {"ts", "hello.ts", "typescript"},
                {"jsx", "hello.jsx", "jsx"},
                {"tsx", "hello.tsx", "tsx"}}) {
            Path file = paths.serverScripts().resolve("examples/" + entry[0] + "/" + entry[1]);
            NekoPreparedModule prepared = cache.prepare(file);

            assertEquals(entry[2], prepared.languageId(), entry[1] + " language identity");
            assertNotNull(prepared.sourceMap(), entry[1] + " must publish a source map");
            SourceMapRegistry registry = new SourceMapRegistry(paths.root());
            registry.register(prepared.sourcePath(), prepared.sourceMap());
            SourceMapRegistry.OriginalPosition mapped = registry.getMappedPosition(prepared.sourcePath(), 1, 1);
            assertEquals(prepared.sourcePath().replace('\\', '/'), mapped.path,
                    entry[1] + " must map back onto its authored file");
        }
    }

    private void installExamples() throws Exception {
        for (String flavor : new String[]{"ts", "jsx", "tsx"}) {
            for (String file : new String[]{"hello.ts", "hello.jsx", "hello.tsx", "greet.ts", "greet.jsx", "greet.tsx"}) {
                String resource = "/nekojs/language-ts-examples/" + flavor + "/" + file;
                if (getClass().getResource(resource) == null) {
                    continue;
                }
                copyExample(flavor, file);
            }
        }
    }

    private void copyExample(String flavor, String file) throws Exception {
        String resource = "/nekojs/language-ts-examples/" + flavor + "/" + file;
        try (var in = getClass().getResourceAsStream(resource)) {
            assertNotNull(in, "example must exist: " + resource);
            String source = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            Path target = paths.serverScripts().resolve("examples/" + flavor + "/" + file);
            Files.createDirectories(target.getParent());
            Files.writeString(target, source);
        }
    }

    private static Value asValue(Object exports) {
        assertTrue(exports instanceof Value, "host must return a guest Value, was: " + exports);
        return (Value) exports;
    }

    private static void assertTrue(boolean condition, String message) {
        org.junit.jupiter.api.Assertions.assertTrue(condition, message);
    }

    private static NekoJSPaths pathsFor(Path gameDir) throws Exception {
        Constructor<NekoJSPaths> constructor = NekoJSPaths.class.getDeclaredConstructor(Path.class);
        constructor.setAccessible(true);
        return constructor.newInstance(gameDir);
    }
}
