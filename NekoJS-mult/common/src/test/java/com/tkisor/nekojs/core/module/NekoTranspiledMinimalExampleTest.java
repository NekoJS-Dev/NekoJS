package com.tkisor.nekojs.core.module;

import com.tkisor.nekojs.core.ScriptFilePolicy;
import com.tkisor.nekojs.core.compiler.NekoModuleMode;
import com.tkisor.nekojs.core.compiler.ScriptCompilerRegistry;
import com.tkisor.nekojs.core.config.SandboxConfig;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 工单 12 AC9：随实现交付 {@code .ts} / {@code .jsx} / {@code .tsx} 最小可运行示例，
 * 且<strong>示例被真实执行</strong>——示例与实现漂移即失败（不是静态文本断言）。
 *
 * <p>示例位于 {@code docs/architecture-refactor/baseline/2026-09-13-language-ts/}，
 * 通过仓库根相对路径读取（测试工作目录含 {@code common/}）。
 */
class NekoTranspiledMinimalExampleTest {

    private static final String EXAMPLE_DIR =
            "docs/architecture-refactor/baseline/2026-09-13-language-ts";

    private NekoJSPaths paths;
    private Context context;
    private NekoScriptModuleLoaderHost host;
    private NekoModulePreparation preparation;
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
        compilers.register(com.tkisor.nekojs.core.compiler.NekoTypeScriptLanguagePlugin.INSTANCE);
        compilers.register(com.tkisor.nekojs.core.compiler.NekoJsxLanguagePlugin.INSTANCE);
        preparation = TestModulePreparation.create(paths, compilers, config);
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
        // classic JSX runtime 全局 factory（生产由 nekojs/node/internal/define.js 注入）
        context.eval("js", """
                globalThis.__nekoJsxFactory = (type, props, ...children) => ({ type, props, children });
                globalThis.__nekoJsxFragment = (...children) => ({ type: null, props: null, children });
                """);
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
        Path target = paths.serverScripts().resolve("minimal-examples/" + exampleName);
        Files.createDirectories(target.getParent());
        Files.copy(example(exampleName), target, StandardCopyOption.REPLACE_EXISTING);
        written.add(target);
        return target;
    }

    /** 示例的 language id / module mode 与其文档说明一致（三种扩展名各自的身份）。 */
    @Test
    void examplesDeclareTheIdentityTheyDocument() throws IOException {
        Path ts = install("minimal-example.ts");
        Path jsx = install("minimal-example.jsx");
        Path tsx = install("minimal-example.tsx");

        NekoPreparedModule tsModule = preparation.prepare(ts);
        assertEquals("typescript", tsModule.languageId(), "示例说明：.ts 的 language id 是 typescript");
        assertEquals(NekoModuleMode.ESM, tsModule.mode(), "示例说明：带 export 的 .ts 是 ESM");
        assertNotNull(tsModule.sourceMap(), "示例说明：.ts 产出非空 source map");

        NekoPreparedModule jsxModule = preparation.prepare(jsx);
        assertEquals("jsx", jsxModule.languageId(), "示例说明：.jsx 的 language id 是 jsx");
        assertEquals(NekoModuleMode.ESM, jsxModule.mode(), "示例说明：带 export 的 .jsx 是 ESM");
        assertNotNull(jsxModule.sourceMap(), "示例说明：.jsx 产出非空 source map");

        NekoPreparedModule tsxModule = preparation.prepare(tsx);
        assertEquals("jsx", tsxModule.languageId(),
                "示例说明：.tsx 归 JSX 语言族，language id 与 .jsx 相同");
        assertEquals(jsxModule.languageId(), tsxModule.languageId(),
                "示例说明：.jsx 与 .tsx 必须报同一个语言身份");
        assertEquals(NekoModuleMode.ESM, tsxModule.mode());
        assertNotNull(tsxModule.sourceMap(), "示例说明：.tsx 产出非空 source map");
    }

    /**
     * 示例被<strong>真实执行</strong>：入口示例混合 import .ts/.jsx/.tsx 并写出 globalThis.result，
     * 期望值是示例自己承诺的四段拼接。
     */
    @Test
    void exampleEntryReallyExecutesMixOfTsJsxAndTsx() throws IOException {
        install("minimal-example.ts");
        install("minimal-example.jsx");
        install("minimal-example.tsx");
        install("minimal-example-entry.mjs");

        Object namespace = host.loadEntry("./server_scripts/minimal-examples/minimal-example-entry.mjs");
        assertNotNull(namespace, "示例入口必须能 link 并求值");

        assertEquals("hello world|84|hello world|x", context.getBindings("js").getMember("result").asString(),
                "示例承诺的真实执行结果必须一致（greet / double(answer) / 元素文本 / tsx 组件文本）");
    }

    /** .tsx 示例的 TS 层真的生效：interface 与类型标注不留在产物里，JSX 结构照常 lowering。 */
    @Test
    void tsxExampleErasesTypesAndKeepsJsx() throws IOException {
        Path tsx = install("minimal-example.tsx");
        NekoPreparedModule prepared = preparation.prepare(tsx);

        assertTrue(prepared.code().contains("__nekoJsxFactory"), "JSX 必须 lowering：\n" + prepared.code());
        assertFalse(prepared.code().contains("interface Item"), "interface 声明必须被擦除：\n" + prepared.code());
        assertFalse(prepared.code().contains(": Item"), "类型标注必须被擦除：\n" + prepared.code());
        assertTrue(prepared.code().contains("label"), "运行时字段必须保留：\n" + prepared.code());
    }

    /** 迁移材料存在且被示例引用（工单 12 要求"必要迁移材料"）。 */
    @Test
    void migrationMaterialExists() {
        Path migration = repoRoot().resolve(EXAMPLE_DIR).resolve("MIGRATION.md");
        assertTrue(Files.exists(migration), "迁移材料必须存在: " + migration);
    }
}
