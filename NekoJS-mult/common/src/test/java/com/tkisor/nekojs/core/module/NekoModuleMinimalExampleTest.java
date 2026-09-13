package com.tkisor.nekojs.core.module;

import com.tkisor.nekojs.core.ScriptFilePolicy;
import com.tkisor.nekojs.core.compiler.NekoCompilationPipeline;
import com.tkisor.nekojs.core.compiler.NekoModuleMode;
import com.tkisor.nekojs.core.compiler.ScriptCompilerRegistry;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.fs.NekoJSFileSystem;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.fs.NekoModuleReadService;
import com.tkisor.nekojs.core.fs.SandboxPolicy;
import com.tkisor.nekojs.testfixture.TestModulePreparation;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.HostAccess;
import graal.graalvm.polyglot.Source;
import graal.graalvm.polyglot.Value;
import graal.graalvm.polyglot.io.IOAccess;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 工单 11 AC11：随实现交付 JS / CJS / ESM 的最小可运行示例，且<strong>示例只使用已通过 gate
 * 的模块能力，缓存/reload 行为与示例说明一致</strong>。
 *
 * <p>本测试把 {@code docs/architecture-refactor/baseline/2026-09-12-language-pipeline/} 下的
 * 示例文件当作真实脚本源执行——示例若与实现漂移（例如说明里承诺的模块模式或缓存行为变了），
 * 本测试会失败。示例文件通过仓库根相对路径读取（测试工作目录含 {@code common/}）。
 */
class NekoModuleMinimalExampleTest {

    private static final String EXAMPLE_DIR =
            "docs/architecture-refactor/baseline/2026-09-12-language-pipeline";

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
    }

    @AfterEach
    void tearDown() throws IOException {
        context.close();
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

    /** 把示例文件复制进脚本目录（示例本身放在 docs 下，不污染脚本目录）。 */
    private Path install(String exampleName, String scriptName) throws IOException {
        Path target = paths.serverScripts().resolve("minimal-examples/" + scriptName);
        Files.createDirectories(target.getParent());
        Files.copy(example(exampleName), target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        written.add(target);
        return target;
    }

    /**
     * 示例的模块模式与说明一致：.js → AUTO（无 import/export → COMMONJS）、
     * .cjs → COMMONJS、.mjs → ESM。
     */
    @Test
    void examplesDeclareTheModuleModeTheyDocument() throws IOException {
        Path plainJs = install("minimal-example.js", "minimal-example.js");
        Path cjs = install("minimal-example.cjs", "minimal-example.cjs");
        Path mjs = install("minimal-example.mjs", "minimal-example.mjs");

        NekoPreparedModule plain = preparation.prepare(plainJs);
        NekoPreparedModule common = preparation.prepare(cjs);
        NekoPreparedModule esm = preparation.prepare(mjs);

        assertEquals("javascript", plain.languageId());
        assertEquals(NekoModuleMode.AUTO, plain.requestedMode(), "示例说明：.js 的 requested mode 是 AUTO");
        assertEquals(NekoModuleMode.COMMONJS, plain.mode(), "示例说明：无 import/export 的 .js 判定为 COMMONJS");

        assertEquals(NekoModuleMode.COMMONJS, common.requestedMode(), "示例说明：.cjs 强制 COMMONJS");
        assertEquals(NekoModuleMode.COMMONJS, common.mode());
        assertNotNull(common.cjsRecord(), "示例说明：CJS 准备阶段附带静态分析记录");

        assertEquals(NekoModuleMode.ESM, esm.requestedMode(), "示例说明：.mjs 强制 ESM");
        assertEquals(NekoModuleMode.ESM, esm.mode());
        assertNotNull(esm.esmAst(), "示例说明：ESM 准备阶段附带 AST 供 link 使用");
    }

    /** ESM 示例 + 入口示例真实执行：import/link/执行链路可用。 */
    @Test
    void esmEntryExampleImportsAndExecutes() throws IOException {
        install("minimal-example.mjs", "minimal-example.mjs");
        install("minimal-example-entry.mjs", "minimal-example-entry.mjs");

        Object namespace = host.loadEntry("./server_scripts/minimal-examples/minimal-example-entry.mjs");

        assertNotNull(namespace, "ESM 入口示例必须能 link 并求值");
    }

    /** CJS 示例真实执行：module.exports 可用（示例说明的 CJS 语义）。 */
    @Test
    void cjsExampleExportsAreUsable() throws IOException {
        install("minimal-example.cjs", "minimal-example.cjs");

        Object exports = host.loadEntry("./server_scripts/minimal-examples/minimal-example.cjs");

        assertNotNull(exports, "CJS 示例必须返回 exports");
        Value value = (Value) exports;
        assertEquals("hello world", value.getMember("greet").execute("world").asString(),
                "示例承诺的 greet() 必须可用");
    }

    /** JS 示例真实执行（无 import/export 的 AUTO 判定路径）。 */
    @Test
    void plainJsExampleLoads() throws IOException {
        install("minimal-example.js", "minimal-example.js");

        Object exports = host.loadEntry("./server_scripts/minimal-examples/minimal-example.js");

        assertNotNull(exports, "普通 JS 示例必须能加载");
    }

    /**
     * 示例说明承诺的缓存行为必须与实现一致：未变化命中，编辑后失效并重编译。
     */
    @Test
    void exampleCacheBehaviourMatchesItsDocumentation() throws IOException {
        Path cjs = install("minimal-example.cjs", "minimal-example.cjs");

        NekoPreparedModule first = preparation.prepare(cjs);
        assertTrue(first == preparation.prepare(cjs),
                "示例说明：文件未变化时命中缓存，不重新准备");

        Files.writeString(cjs, "module.exports = { greet: (n) => 'changed ' + n };\n");
        NekoPreparedModule afterEdit = preparation.prepare(cjs);

        assertTrue(afterEdit != first, "示例说明：内容变化使旧条目失效");
        assertTrue(afterEdit.code().contains("changed"),
                "示例说明：失效后必须重新准备新内容");
    }
}
