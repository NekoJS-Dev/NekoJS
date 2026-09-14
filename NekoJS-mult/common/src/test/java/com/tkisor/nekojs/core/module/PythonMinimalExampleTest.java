package com.tkisor.nekojs.core.module;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.data.ScriptId;
import com.tkisor.nekojs.core.ScriptFilePolicy;
import com.tkisor.nekojs.core.compiler.NekoModuleMode;
import com.tkisor.nekojs.core.compiler.ScriptCompilerRegistry;
import com.tkisor.nekojs.core.compiler.python.PythonToJsCompiler;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.error.DefaultErrorTracker;
import com.tkisor.nekojs.core.error.ScriptError;
import com.tkisor.nekojs.core.error.SourceMapRegistry;
import com.tkisor.nekojs.core.fs.NekoJSFileSystem;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.fs.NekoModuleReadService;
import com.tkisor.nekojs.core.fs.SandboxPolicy;
import com.tkisor.nekojs.testfixture.TestModulePreparation;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.HostAccess;
import graal.graalvm.polyglot.Source;
import graal.graalvm.polyglot.io.IOAccess;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 工单 13 AC9：随实现交付 {@code .py} 最小可运行示例，
 * 且<strong>示例被真实执行</strong>——示例与实现漂移即失败（不是静态文本断言）。
 *
 * <p>示例位于 {@code docs/architecture-refactor/baseline/2026-09-14-language-python/}，
 * 通过仓库根相对路径读取（测试工作目录含 {@code common/}）。
 */
class PythonMinimalExampleTest {

    private static final String EXAMPLE_DIR =
            "docs/architecture-refactor/baseline/2026-09-14-language-python";

    private NekoJSPaths paths;
    private Context context;
    private NekoScriptModuleLoaderHost host;
    private NekoModulePreparation preparation;
    private DefaultErrorTracker tracker;
    private final List<Path> written = new ArrayList<>();

    @BeforeAll
    static void initPlatform() {
        TestPlatformInit.ensureInitialized();
    }

    @BeforeEach
    void setUp() throws IOException {
        paths = NekoJSPaths.get();
        SandboxConfig config = SandboxConfig.defaultConfig();
        ScriptCompilerRegistry compilers = ScriptCompilerRegistry.createRuntimeRegistry();
        compilers.registerLanguage("python", Set.of(".py"), new PythonToJsCompiler());
        preparation = TestModulePreparation.create(paths, compilers, config);
        tracker = new DefaultErrorTracker(paths, config);
        NekoModuleReadService readService = new NekoModuleReadService(preparation, new ScriptFilePolicy(compilers));
        IOAccess ioAccess = IOAccess.newBuilder()
                .fileSystem(new NekoJSFileSystem(paths.root(), new SandboxPolicy(config, paths), readService))
                .build();
        context = Context.newBuilder("js")
                .allowExperimentalOptions(true)
                .allowHostAccess(HostAccess.ALL)
                .allowIO(ioAccess)
                .option("js.ecmascript-version", "latest")
                .option("js.commonjs-require", "true")
                .option("js.commonjs-require-cwd", paths.root().toAbsolutePath().toString())
                .build();
        host = new NekoScriptModuleLoaderHost(context,
                new NekoModuleResolver(paths, new ScriptFilePolicy(compilers)), paths, preparation);
        context.getBindings("js").putMember("__nekoScriptModuleLoaderHost", host);
        try (var in = getClass().getResourceAsStream("/nekojs/node/internal/script-loader.js")) {
            assertNotNull(in, "script-loader.js must be on the test classpath");
            context.eval(Source.newBuilder("js", new String(in.readAllBytes(), StandardCharsets.UTF_8),
                    "nekojs/node/internal/script-loader.js").build());
        }
    }

    @AfterEach
    void tearDown() throws IOException {
        context.close();
        SourceMapRegistry.clear();
        // 真删文件 + 空的父目录：deleteOnExit 只在 JVM 退出时生效，会污染共享 gameDir
        TestModulePreparation.deleteFilesAndEmptyParents(written);
        written.clear();
    }

    private static Path repoRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null && !Files.isDirectory(current.resolve("common/src/main/java"))) {
            current = current.getParent();
        }
        assertNotNull(current, "must locate repository root");
        return current;
    }

    private Path example(String fileName) {
        Path file = repoRoot().resolve(EXAMPLE_DIR).resolve(fileName);
        assertTrue(Files.exists(file), "example file must exist: " + file);
        return file;
    }

    private Path install(String exampleName) throws IOException {
        Path target = paths.serverScripts().resolve("minimal-python/" + exampleName);
        Files.createDirectories(target.getParent());
        Files.copy(example(exampleName), target, StandardCopyOption.REPLACE_EXISTING);
        written.add(target);
        return target;
    }

    /** 示例声明的身份：.py 的 language id 是 python，带顶层绑定的模块是 ESM，且 source map 非空。 */
    @Test
    void exampleDeclaresTheIdentityItDocuments() throws IOException {
        Path python = install("minimal-example.py");
        NekoPreparedModule module = preparation.prepare(python);

        assertEquals("python", module.languageId(), "示例说明：.py 的 language id 是 python");
        assertEquals(NekoModuleMode.AUTO, module.requestedMode(), "示例说明：.py 请求 AUTO 模式");
        assertEquals(NekoModuleMode.ESM, module.mode(), "示例说明：带顶层绑定的 .py 解析为 ESM");
        assertNotNull(module.sourceMap(), "示例说明：.py 产出非空 source map");
    }

    /** 示例被真实执行：入口 import .py 并写出 globalThis.pythonExampleResult，值是示例自己承诺的拼接。 */
    @Test
    void exampleEntryReallyExecutesAndProducesDocumentedResult() throws IOException {
        install("minimal-example.py");
        install("minimal-example-entry.mjs");

        Object namespace = host.loadEntry("./server_scripts/minimal-python/minimal-example-entry.mjs");
        assertNotNull(namespace, "示例入口必须能 link 并求值");

        assertEquals("hello world|6|30|1|16",
                context.getBindings("js").getMember("pythonExampleResult").asString(),
                "示例承诺的真实执行结果必须一致"
                        + "（greet / Counter 累加 / squares 求和 / 首尾元素）");
    }

    /** 示例里的 raise 经 source map 回到原始 .py 的 raise 行（诊断能力也是示例承诺的一部分）。 */
    @Test
    void exampleRaiseMapsBackToThePythonRaiseLine() throws IOException {
        Path python = install("minimal-example.py");
        written.add(paths.serverScripts().resolve("minimal-python/example-boom-entry.mjs"));
        Files.writeString(paths.serverScripts().resolve("minimal-python/example-boom-entry.mjs"),
                "import { boom } from './minimal-example.py';\nboom(1);\n");

        Throwable failure = assertThrows(Throwable.class,
                () -> host.loadEntry("./server_scripts/minimal-python/example-boom-entry.mjs"));
        ScriptError error = ScriptError.create(ScriptType.SERVER,
                ScriptId.of("rt", "python-example-boom"), "example-boom-entry.mjs", failure, tracker);

        assertEquals(paths.root().relativize(python).toString().replace('\\', '/'), error.getDisplayPath(),
                "示例说明：执行错误回到原始 .py 文件");
        assertEquals(27, error.getLineNumber(),
                "示例说明：执行错误回到原始 .py 的 raise 行（不是转译产物中注入的 helper 行）");
        assertTrue(error.getErrorMessage().contains("python-example-boom"));
    }

    /** 迁移材料存在且与示例同目录（工单要求「必要迁移材料」）。 */
    @Test
    void migrationMaterialExists() {
        assertTrue(Files.exists(repoRoot().resolve(EXAMPLE_DIR).resolve("MIGRATION.md")),
                "迁移材料必须存在");
    }
}
