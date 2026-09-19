package com.tkisor.nekojs.core.module;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.core.ScriptFilePolicy;
import com.tkisor.nekojs.core.compiler.NekoCompilationPipeline;
import com.tkisor.nekojs.core.compiler.ScriptCompilerRegistry;
import com.tkisor.nekojs.core.compiler.python.PythonToJsCompiler;
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

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 票据 13 AC9：随票交付的 .py 最小可运行示例冒烟。
 *
 * <p>示例源文件在 {@code common/src/test/resources/nekojs/language-py-examples/}（交付物，
 * 内容同时内联于基线迁移材料）。测试把它们拷入隔离 gameDir 后经最高调用者 seam
 * （{@link NekoScriptModuleLoaderHost#loadEntry}）真实装载，断言文档承诺的输出与
 * language 身份 / source-map 行为一致。示例只使用已过 gate 的 Python 能力：
 * 缩进与注释、def/elif/f-string、兄弟 .py 模块 import、被剥离的 {@code from nekojs import *}。
 */
class PythonExamplesSmokeTest {

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
        Files.createDirectories(paths.serverScripts().resolve("examples/py"));
        ScriptCompilerRegistry compilers = ScriptCompilerRegistry.createRuntimeRegistry();
        compilers.registerLanguage("python", Set.of(".py"), new PythonToJsCompiler());
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
        copyExample("hello.py");
        copyExample("greet.py");
    }

    @AfterEach
    void tearDown() {
        if (context != null) {
            context.close();
        }
    }

    @Test
    void pythonExampleRunsWithDocumentedOutput() throws Exception {
        Value exports = asValue(host.loadEntry("./server_scripts/examples/py/hello.py"));

        assertEquals("hello, neko!", exports.getMember("value").asString(),
                "indentation, elif and f-string semantics must survive transpilation");
        assertEquals("py", exports.getMember("tag").asString(),
                "the sibling .py module's exported name must be importable through the shared resolver");
    }

    @Test
    void documentedLanguageIdentityAndSourceMapHold() throws Exception {
        Path file = paths.serverScripts().resolve("examples/py/hello.py");
        NekoPreparedModule prepared = cache.prepare(file);

        assertEquals("python", prepared.languageId(), ".py must keep its Python language identity");
        assertNotNull(prepared.sourceMap(), ".py must publish a source map");
        assertEquals(0, prepared.prependedLineCount(),
                "prepended helper lines are absorbed by the map, not counted ahead of it");

        SourceMapRegistry registry = new SourceMapRegistry(paths.root());
        registry.register(prepared.sourcePath(), prepared.sourceMap(), prepared.prependedLineCount());
        SourceMapRegistry.OriginalPosition mapped = registry.getMappedPosition(prepared.sourcePath(), 1, 1);
        assertEquals(prepared.sourcePath().replace('\\', '/'), mapped.path.replace('\\', '/'),
                "the example must map back onto its authored .py file, not a generated virtual module");
        assertTrue(prepared.code().contains("function describe"), prepared.code());
        assertTrue(prepared.code().contains("./greet"), "the sibling import must stay a module import: " + prepared.code());

        // 兄弟模块自己的产物保留问候语字面量，并被导出供 import 使用。
        NekoPreparedModule greet = cache.prepare(paths.serverScripts().resolve("examples/py/greet.py"));
        assertTrue(greet.code().contains("hello, "), greet.code());
        assertTrue(greet.code().contains("export { greet, TAG }") || greet.code().contains("export { TAG, greet }"),
                "top-level Python definitions must be exported for sibling imports: " + greet.code());
    }

    private void copyExample(String file) throws IOException {
        String resource = "/nekojs/language-py-examples/py/" + file;
        try (var in = getClass().getResourceAsStream(resource)) {
            assertNotNull(in, "example must exist: " + resource);
            String source = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            Files.writeString(paths.serverScripts().resolve("examples/py/" + file), source);
        }
    }

    private static Value asValue(Object exports) {
        assertTrue(exports instanceof Value, "host must return a guest Value, was: " + exports);
        return (Value) exports;
    }

    private static NekoJSPaths pathsFor(Path gameDir) throws Exception {
        Constructor<NekoJSPaths> constructor = NekoJSPaths.class.getDeclaredConstructor(Path.class);
        constructor.setAccessible(true);
        return constructor.newInstance(gameDir);
    }
}
