package com.tkisor.nekojs.core.module;

import com.tkisor.nekojs.core.ScriptFilePolicy;
import com.tkisor.nekojs.core.compiler.NekoModuleMode;
import com.tkisor.nekojs.core.compiler.ScriptCompilerRegistry;
import com.tkisor.nekojs.core.compiler.python.PythonToJsCompiler;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 工单 13 AC2（导入行为）与 AC4/AC5（依赖维度 cache invalidation）。
 *
 * <p>Python 的 {@code import} 经<strong>真实模块系统</strong>验证：{@code .py} 里的
 * {@code import helper} / {@code from helper import sym} 由 {@code PythonEmitter} 转成 ESM
 * {@code import}，必须能被 {@link NekoScriptModuleLoaderHost} 解析、link 并求值——
 * 而不是只断言产物字符串（旧 {@code PythonToJsCompilerTest} 只做到字符串断言）。
 *
 * <p>导入的本地绑定语义（{@code PythonEmitter.esmNamespaceImport} 的既有契约）：
 * {@code import m} → 绑定 {@code m}；{@code import a.b.c} → 绑定<strong>叶子段</strong> {@code c}
 * （因此访问写 {@code c.fn()}，path 为 {@code ./a/b/c}）；模块名必须是合法 Python 标识符
 * （连字符会先被 Python 词法/语法拒绝，见 {@link #pythonRejectsNonIdentifierImportName}）。
 */
class PythonModuleImportCorpusTest {

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
        compilers.registerLanguage("python", Set.of(".py"), new PythonToJsCompiler());
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
        SourceMapRegistry.clear();
        // 真删文件 + 空的父目录：deleteOnExit 只在 JVM 退出时生效，会污染共享 gameDir
        TestModulePreparation.deleteFilesAndEmptyParents(written);
        written.clear();
    }

    private Path write(String name, String source) throws IOException {
        Path file = paths.serverScripts().resolve("language-py-import/" + name);
        Files.createDirectories(file.getParent());
        Files.writeString(file, source);
        written.add(file);
        return file;
    }

    private void load(String entry) throws IOException {
        assertNotNull(host.loadEntry("./server_scripts/language-py-import/" + entry),
                entry + " 必须能 link 并求值");
    }

    private List<String> globalList(String name) {
        var array = context.getBindings("js").getMember(name);
        List<String> out = new ArrayList<>();
        for (long i = 0; i < array.getArraySize(); i++) {
            out.add(String.valueOf(array.getArrayElement(i)));
        }
        return out;
    }

    // ================= AC2：导入行为固定（真实 link + 求值） =================

    /** {@code import helper} → ESM namespace import：跨 .py 的模块解析、link、求值必须真的能跑。 */
    @Test
    void pythonImportLinksAndExecutesAcrossModules() throws IOException {
        write("alib.py", """
                def label():
                    return 'lib-1'

                CONSTANT = 41
                """);
        write("amain.py", """
                import alib

                globalThis.importPair = [alib.label(), str(alib.CONSTANT + 1)]
                """);

        load("amain.py");
        assertEquals(List.of("lib-1", "42"), globalList("importPair"),
                "跨 .py 的 import 必须真的解析、link 并执行（不是只断言产物字符串）");
    }

    /** {@code from helper import a, b as c} → ESM 具名导入；同模块多个符号必须各自可用。 */
    @Test
    void pythonFromImportBindsNamedSymbols() throws IOException {
        write("bmath.py", """
                def double(n):
                    return n * 2

                def triple(n):
                    return n * 3
                """);
        write("bnamed.py", """
                from bmath import double
                from bmath import triple as t

                globalThis.namedPair = [str(double(5)), str(t(5))]
                """);

        load("bnamed.py");
        assertEquals(List.of("10", "15"), globalList("namedPair"),
                "from ... import <name> / as <alias> 都必须绑定到正确符号");
    }

    /** {@code import a.b.c} → 路径 {@code ./a/b/c}、本地绑定是叶子段 {@code c}（既有转译契约）。 */
    @Test
    void pythonDottedImportResolvesThroughDirectories() throws IOException {
        write("pkg/util.py", """
                def tag():
                    return 'deep'
                """);
        // 既有转译契约：import a.b.c 的本地绑定是叶子段 c，因此正文写 util.tag()
        write("dotted.py", """
                import pkg.util

                globalThis.dottedResult = util.tag()
                """);

        load("dotted.py");
        assertEquals("deep", context.getBindings("js").getMember("dottedResult").asString(),
                "import a.b.c 必须解析为 ./a/b/c，本地绑定是叶子段（既有转译契约）");
    }

    /**
     * 模块名必须是合法 Python 标识符：连字符写法在准备阶段就被词法/语法拒绝，
     * 不会静默产出一个永远解析不到的 import。
     */
    @Test
    void pythonRejectsNonIdentifierImportName() throws IOException {
        Path file = write("badimport.py", "import shape-lib\n");

        Exception failure = org.junit.jupiter.api.Assertions.assertThrows(Exception.class,
                () -> preparation.prepare(file),
                "非标识符模块名必须在准备阶段暴露，而不是静默产出坏 import");
        String message = String.valueOf(failure.getMessage());
        assertTrue(message.contains("badimport.py"),
                "准备期错误必须携带原始 .py 文件：" + message);
    }

    /** 被导入的 .py 必须携带自己的语言身份与 cache key（模块归属可追踪）。 */
    @Test
    void importedPythonModuleCarriesItsOwnIdentity() throws IOException {
        Path lib = write("id_lib.py", "def f():\n    return 7\n");
        write("id_main.py", "import id_lib\n\nglobalThis.idValue = id_lib.f()\n");

        load("id_main.py");
        assertEquals(7, context.getBindings("js").getMember("idValue").asInt(),
                "被导入 .py 的函数必须可调用且返回正确值");

        NekoPreparedModule preparedLib = preparation.prepare(lib);
        assertEquals("python", preparedLib.languageId(), "被导入的 .py 必须携带 python 语言身份");
        assertEquals(NekoModuleMode.ESM, preparedLib.mode(), "被导入的 .py 模块模式是身份的一部分");
        assertNotNull(preparedLib.cacheKey(), "被导入的 .py 必须携带稳定 cache key");
    }

    // ================= AC4/AC5：依赖维度 cache invalidation =================

    /**
     * 依赖变化不得命中旧 Python 产物：改动被 {@code import} 的 .py 后，依赖图必须把入口标为受影响，
     * 失效并重新加载后必须看到新值。
     */
    @Test
    void dependencyChangeInvalidatesImportingEntry() throws IOException {
        Path helper = write("dep_helper.py", "def value():\n    return 1\n");
        Path entry = write("dep_entry.py",
                "import dep_helper\n\nglobalThis.depValue = dep_helper.value()\n");
        String helperId = "server_scripts/language-py-import/dep_helper.py";
        String entryId = "server_scripts/language-py-import/dep_entry.py";

        load("dep_entry.py");
        assertEquals(1, context.getBindings("js").getMember("depValue").asInt(),
                "测试前提：首次加载必须执行依赖模块");

        // 依赖内容变化
        Files.writeString(helper, "def value():\n    return 2\n");

        assertTrue(host.affectedEntries(helperId).contains(entryId),
                "改动 helper 必须经依赖图把 entry 标为受影响入口，实际=" + host.affectedEntries(helperId));

        host.invalidateAffectedModules(helperId);
        assertFalse(preparation.isCached(entry), "失效后入口的 prepared 条目必须被清除");
        assertFalse(preparation.isCached(helper), "失效后依赖的 prepared 条目必须被清除");

        load("dep_entry.py");
        assertEquals(2, context.getBindings("js").getMember("depValue").asInt(),
                "依赖变化后重新加载必须看到新值——不得命中旧 Python 产物");
    }

    /** mode 维度：同一路径、内容相同，但扩展名请求的 mode 不同 → identity / cache key 必须不同。 */
    @Test
    void requestedModeParticipatesInPythonCacheKey() throws IOException {
        ScriptCompilerRegistry registry = ScriptCompilerRegistry.createRuntimeRegistry();
        registry.registerLanguage("python", Set.of(".py", ".mjs"), new PythonToJsCompiler());
        NekoModulePreparation prep = TestModulePreparation.create(paths, registry, SandboxConfig.defaultConfig());

        Path py = write("mode_probe.py", "MODE_VALUE = 1\n");
        Path mjs = write("mode_probe.mjs", "MODE_VALUE = 1\n");

        NekoPreparedModule fromPy = prep.prepare(py);
        NekoPreparedModule fromMjs = prep.prepare(mjs);

        assertEquals(NekoModuleMode.AUTO, fromPy.requestedMode(), ".py 请求 AUTO");
        assertEquals(NekoModuleMode.ESM, fromMjs.requestedMode(), ".mjs 请求 ESM");
        assertFalse(prep.pipeline().identityOf(py).equals(prep.pipeline().identityOf(mjs)),
                "同一语言插件下，扩展名请求的 mode 不同 → identity 必须不同");

        // cache key 同时含 path，两条不同路径的 key 本来就不同，因此不足以证明 mode 参与。
        // 直接固定 key 的构造：同 path、同内容、仅 mode 不同 → key 必须不同。
        java.nio.file.Path samePath = paths.serverScripts().resolve("language-py-import/mode-key-probe.mjs");
        NekoModuleCacheKey autoKey = NekoModuleCacheKey.of("python", NekoModuleMode.AUTO, samePath, "MODE_VALUE = 1\n");
        NekoModuleCacheKey esmKey = NekoModuleCacheKey.of("python", NekoModuleMode.ESM, samePath, "MODE_VALUE = 1\n");
        assertFalse(autoKey.equals(esmKey),
                "mode 必须进入 cache key：同路径同内容、仅 mode 不同时不得命中同一条目");
        assertFalse(autoKey.identity().equals(esmKey.identity()),
                "mode 必须进入 identity（cache 命中判定维度）");
    }

    /** 解析后的 mode 变化（裸表达式 → 带顶层绑定）必须失效：产物模式是身份的一部分。 */
    @Test
    void resolvedModeTransitionInvalidatesPythonCache() throws IOException {
        Path file = write("resolved_mode.py", "1 + 2\n");
        NekoPreparedModule commonJs = preparation.prepare(file);
        assertEquals(NekoModuleMode.COMMONJS, commonJs.mode(), "裸表达式产物无 export → COMMONJS");

        Files.writeString(file, "RESOLVED = 1 + 2\n");
        NekoPreparedModule esm = preparation.prepare(file);
        assertEquals(NekoModuleMode.ESM, esm.mode(), "带顶层绑定产物有 export → ESM");
        assertFalse(commonJs.cacheKey().equals(esm.cacheKey()), "内容变化必须改变 cache key");
        assertFalse(commonJs == esm, "解析模式变化后必须得到新产物，不得命中旧的 COMMONJS 产物");
    }
}
