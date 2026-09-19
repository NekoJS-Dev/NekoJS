package com.tkisor.nekojs.core.module;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.core.ScriptFilePolicy;
import com.tkisor.nekojs.core.compiler.NekoCompilationPipeline;
import com.tkisor.nekojs.core.compiler.NekoModuleMode;
import com.tkisor.nekojs.core.compiler.ScriptCompilerRegistry;
import com.tkisor.nekojs.core.compiler.python.PythonToJsCompiler;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.error.DefaultErrorTracker;
import com.tkisor.nekojs.core.error.SourceMapRegistry;
import com.tkisor.nekojs.core.fs.NekoJSFileSystem;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.fs.SandboxPolicy;
import com.tkisor.nekojs.core.module.esm.NekoEsmLinkException;
import com.tkisor.nekojs.core.node.NekoNodeModuleInstaller;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.PolyglotException;
import graal.graalvm.polyglot.Value;
import graal.graalvm.polyglot.io.IOAccess;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 票据 13 AC1/AC2（执行侧）/ AC3（执行侧）/ AC4 / AC6：Python 转译产物经统一
 * Resolution/Cache 与真实 Graal 执行；既有 Python 语义（缩进、定义/类/调用、注释、
 * 模块 import）由执行结果固定；运行时异常经 source map 回到 authored .py 行列并保留
 * 模块身份；Python 与 JS/ESM 混合加载的错误阶段和模块归属可观察；依赖变化使缓存失效。
 *
 * <p>观测点是最高调用者 seam：{@code loadEntry}/{@code loadEntryAsync} 的返回值、
 * 异常对象字段（stage/owner/sourcePath/sourceLine/sourceColumn/moduleId）、guest cause
 * 与 shared prepared cache。不读私有 parser 对象身份。
 */
class NekoPythonRuntimeTest {

    @TempDir
    Path gameDir;

    private NekoJSPaths paths;
    private ScriptCompilerRegistry compilers;
    private NekoModulePipelineCache cache;
    private Context context;
    private NekoScriptModuleLoaderHost host;

    @BeforeEach
    void setUp() throws Exception {
        TestPlatformInit.ensureInitialized(gameDir);
        paths = pathsFor(gameDir);
        Files.createDirectories(paths.serverScripts().resolve("src"));
        compilers = ScriptCompilerRegistry.createRuntimeRegistry();
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
    }

    @AfterEach
    void tearDown() {
        if (context != null) {
            context.close();
        }
    }

    private Path write(String name, String source) throws IOException {
        Path file = paths.serverScripts().resolve("src/" + name);
        Files.createDirectories(file.getParent());
        Files.writeString(file, source);
        return file;
    }

    // ---- AC1/AC2: existing Python semantics run through the unified pipeline ----

    @Test
    void indentationDefinitionsCallsAndCommentsRunUnchanged() throws Exception {
        write("semantics.py", "# leading comment stays inert\n"
                + "def classify(n):\n"
                + "    if n < 0:\n"
                + "        return 'neg'\n"
                + "    elif n == 0:\n"
                + "        return 'zero'\n"
                + "    else:\n"
                + "        # a nested comment must not shift the generated lines\n"
                + "        return 'pos'\n"
                + "total = 0\n"
                + "for n in range(-1, 2):\n"
                + "    total += 1\n"
                + "combined = classify(0) + classify(1) + classify(-1) + str(total)\n");

        Value exports = asValue(host.loadEntry("./server_scripts/src/semantics.py"));

        assertEquals("zeroposneg3", exports.getMember("combined").asString(),
                "indentation, else/elif, comments and for-loop semantics must survive transpilation");
    }

    @Test
    void classesAndClosuresRunUnchanged() throws Exception {
        write("classes.py", "class Counter:\n"
                + "    def __init__(self, start=0):\n"
                + "        self.value = start\n"
                + "    def bump(self, by):\n"
                + "        self.value += by\n"
                + "        return self.value\n"
                + "    def label(self):\n"
                + "        return f'n={self.value}'\n"
                + "\n"
                + "def make():\n"
                + "    counter = Counter(10)\n"
                + "    def step():\n"
                + "        return counter.bump(5)\n"
                + "    return step\n"
                + "step = make()\n"
                + "first = step()\n"
                + "second = step()\n"
                + "text = Counter(1).label()\n");

        Value exports = asValue(host.loadEntry("./server_scripts/src/classes.py"));

        assertEquals(15, exports.getMember("first").asInt(), "class method + augmented assignment");
        assertEquals(20, exports.getMember("second").asInt(), "closure must keep mutating the same instance");
        assertEquals("n=1", exports.getMember("text").asString(), "default parameter + f-string");
    }

    @Test
    void pythonModulesImportEachOtherThroughTheSharedResolver() throws Exception {
        write("mathlib.py", "def double(n):\n"
                + "    return n * 2\n"
                + "\n"
                + "TAG = 'mathlib'\n");
        write("uses_math.py", "from mathlib import double, TAG\n"
                + "import mathlib\n"
                + "value = double(21)\n"
                + "tag = TAG\n"
                + "same = mathlib.double is double\n");

        Value exports = asValue(host.loadEntry("./server_scripts/src/uses_math.py"));

        assertEquals(42, exports.getMember("value").asInt(), "named import from a sibling .py module");
        assertEquals("mathlib", exports.getMember("tag").asString());
        assertTrue(exports.getMember("same").asBoolean(),
                "named import and namespace import must expose the same module identity");
    }

    // ---- AC3 (execution side): runtime failures map back to authored .py positions ----

    @Test
    void runtimeFailureInRaisedFunctionReportsAuthoredLineAndIdentity() throws Exception {
        write("boom.py", "def helper(n):\n"
                + "    total = n + 1\n"
                + "    if total > 0:\n"
                + "        raise ValueError('py-boom')\n"
                + "    return total\n"
                + "\n"
                + "helper(1)\n");

        IOException failure = assertThrows(IOException.class,
                () -> host.loadEntry("./server_scripts/src/boom.py"));

        NekoModuleError staged = NekoModulePipelinePrepareTest.assertStaged(
                failure, NekoModuleError.Stage.EXECUTE, NekoModuleError.OWNER_EXECUTION);
        assertTrue(String.valueOf(staged.getMessage()).contains("py-boom"), String.valueOf(staged));
        assertEquals("server_scripts/src/boom.py", staged.sourcePath().replace('\\', '/'));
        assertEquals("server_scripts/src/boom.py", staged.moduleId().replace('\\', '/'),
                "the failing module identity must stay the authored .py module");
        assertEquals(4, staged.sourceLine(),
                "the raise must be reported at its authored Python line, not at a generated prelude line: "
                        + staged.detail());
        assertTrue(staged.sourceColumn() > 0, staged.detail());
        assertInstanceOf(PolyglotException.class, staged.getCause(),
                "the guest cause must remain available for diagnostics");
    }

    @Test
    void runtimeFailureInCommonJsShapedPythonReportsAuthoredLineNotGeneratedLine() throws Exception {
        // 无顶层 define/assign 的脚本是 CJS 形态：产物含 __nekoDiv 等前置助手行。
        write("cjs-mode.py", "from nekojs import *\n"
                + "print('start')\n"
                + "print(10 / 0)\n");

        IOException failure = assertThrows(IOException.class,
                () -> host.loadEntry("./server_scripts/src/cjs-mode.py"));

        NekoModuleError staged = NekoModulePipelinePrepareTest.assertStaged(
                failure, NekoModuleError.Stage.EXECUTE, NekoModuleError.OWNER_EXECUTION);
        assertEquals("server_scripts/src/cjs-mode.py", staged.sourcePath().replace('\\', '/'));
        assertEquals(3, staged.sourceLine(),
                "generated helper lines must not be reported as the authored Python line: " + staged.detail());
        assertTrue(staged.sourceColumn() > 0, staged.detail());
    }

    /** AC3 前半：Python 源解析错误在准备阶段报告 authored 位置（与词法/发射期错误同族）。 */
    @Test
    void pythonSourceParseErrorIsReportedAtPrepareWithTheAuthoredLine() throws Exception {
        // 未闭合的调用在 Python 文法里直到文件末尾才报错（NEWLINE 落在第 4 行），
        // 位置取 parser 实际报告的行列，不编造调用开始行。
        Path file = write("bad_parse_runtime.py", "def f():\n    return 1\nvalue = f(\n");

        Exception failure = assertThrows(Exception.class, () -> cache.prepare(file));

        NekoModuleError staged = NekoModulePipelinePrepareTest.assertStaged(
                failure, NekoModuleError.Stage.PREPARE, NekoModuleError.OWNER_PREPARATION);
        assertEquals("server_scripts/src/bad_parse_runtime.py", staged.sourcePath().replace('\\', '/'));
        assertEquals(4, staged.sourceLine(), staged.detail());
        assertTrue(staged.sourceColumn() > 0, staged.detail());
    }

    /**
     * AC3 后半：**生成 JS 的 link 失败**。Python 的 {@code from <sibling> import <name>} 会转译成
     * ESM 静态 import；被 import 的模块若没有该导出，失败发生在 JS 这一侧（link），而不是
     * Python 源解析侧——
     *
     * <ul>
     *   <li>{@code cache.prepare} 必须**成功**（Python 源合法，转译也成功）；</li>
     *   <li>{@code loadEntry} 抛 {@code Stage.LINK} / {@code Module Resolution/Cache}，
     *       cause 是 {@link NekoEsmLinkException}（真实 JS link 诊断）；</li>
     *   <li>与 {@code EXECUTE}（运行期异常）和 {@code PREPARE}（Python 源错误）可区分。</li>
     * </ul>
     *
     * <p>红侧证据：若把 link 失败错标为 EXECUTE（例如宿主吞掉 {@code NekoEsmLinkException}），
     * 本用例的 stage 断言即失败。
     */
    @Test
    void generatedJsLinkFailureIsDistinguishedFromPythonSourceAndExecutionErrors() throws Exception {
        write("link_sibling.py", "def present():\n"
                + "    return 1\n"
                + "\n"
                + "OTHER = 2\n");
        Path entry = write("link_entry.py", "from link_sibling import absent_name\n"
                + "value = 1\n");

        // 准备阶段看不到该错误：Python 源合法，转译成 `import { absent_name } from './link_sibling'`。
        NekoPreparedModule prepared = cache.prepare(entry);
        assertEquals("python", prepared.languageId());
        assertTrue(prepared.code().contains("import { absent_name }"), prepared.code());
        assertEquals(NekoModuleMode.ESM, prepared.mode());

        IOException failure = assertThrows(IOException.class,
                () -> host.loadEntry("./server_scripts/src/link_entry.py"));

        NekoModuleError staged = NekoModulePipelinePrepareTest.assertStaged(
                failure, NekoModuleError.Stage.LINK, NekoModuleError.OWNER_RESOLUTION_CACHE);
        assertTrue(String.valueOf(staged.getMessage()).contains("absent_name"), String.valueOf(staged));
        assertEquals("server_scripts/src/link_entry.py", staged.sourcePath().replace('\\', '/'),
                "the link failure must name the authored importing .py module: " + staged.detail());
        assertInstanceOf(NekoEsmLinkException.class, staged.getCause(),
                "the diagnostic must be the real generated-JS link failure, not a Python source error");

        // 与 EXECUTE 的区分：同一 sibling 的「存在但运行期抛错」是另一条路径（EXECUTE）。
        write("link_sibling.py", "def present():\n"
                + "    raise ValueError('sibling-runtime-boom')\n");
        write("link_entry.py", "from link_sibling import present\n"
                + "value = present()\n");
        IOException runtimeFailure = assertThrows(IOException.class,
                () -> host.loadEntry("./server_scripts/src/link_entry.py"));
        NekoModuleError runtimeStaged = NekoModulePipelinePrepareTest.assertStaged(
                runtimeFailure, NekoModuleError.Stage.EXECUTE, NekoModuleError.OWNER_EXECUTION);
        assertEquals("server_scripts/src/link_sibling.py", runtimeStaged.sourcePath().replace('\\', '/'),
                "a runtime failure in the sibling must be attributed to the sibling");
    }

    @Test
    void crossImportRuntimeFailureKeepsTheFailingChildPythonIdentity() throws Exception {
        write("child.py", "def explode():\n"
                + "    raise ValueError('child-boom')\n");
        write("outer.py", "from child import explode\n"
                + "explode()\n");

        IOException failure = assertThrows(IOException.class,
                () -> host.loadEntry("./server_scripts/src/outer.py"));

        NekoModuleError staged = NekoModulePipelinePrepareTest.assertStaged(
                failure, NekoModuleError.Stage.EXECUTE, NekoModuleError.OWNER_EXECUTION);
        assertTrue(String.valueOf(staged.getMessage()).contains("child-boom"), String.valueOf(staged));
        assertEquals("server_scripts/src/child.py", staged.sourcePath().replace('\\', '/'),
                "a nested Python failure must name the failing child, not the importing entry");
        assertEquals("server_scripts/src/child.py", staged.moduleId().replace('\\', '/'));
        assertEquals(2, staged.sourceLine(),
                "the child's authored raise line must survive the cross-import boundary: " + staged.detail());
    }

    @Test
    void asyncEntryLoadingOfPythonChildKeepsTheChildIdentityAndAuthoredLine() throws Exception {
        write("dyn_child.py", "def explode():\n"
                + "    raise ValueError('dyn-child-boom')\n"
                + "\n"
                + "explode()\n");
        write("dyn_outer.py", "import dyn_child\n"
                + "\n"
                + "value = 1\n");

        // 异步入口（loadEntryAsync）与同步入口必须归因一致：失败的 .py 子模块带自己的身份与 authored 行。
        Throwable failure;
        try {
            failure = assertThrows(ExecutionException.class,
                    () -> host.loadEntryAsync("./server_scripts/src/dyn_outer.py").get(10, TimeUnit.SECONDS));
        } catch (AssertionError notAFuture) {
            // 宿主在 future 之前同步抛出阶段错误时，直接观测该错误。
            failure = assertThrows(Exception.class,
                    () -> host.loadEntryAsync("./server_scripts/src/dyn_outer.py"));
        }

        NekoModuleError staged = NekoModulePipelinePrepareTest.assertStaged(
                failure, NekoModuleError.Stage.EXECUTE, NekoModuleError.OWNER_EXECUTION);
        assertTrue(String.valueOf(staged.getMessage()).contains("dyn-child-boom"), String.valueOf(staged));
        assertEquals("server_scripts/src/dyn_child.py", staged.sourcePath().replace('\\', '/'), staged.detail());
        assertEquals(4, staged.sourceLine(),
                "the async child's authored raise line must be reported: " + staged.detail());
    }

    // ---- AC4: the same resolution/cache seam invalidates Python modules ----

    @Test
    void changedPythonDependencyIsReloadedThroughTheSharedCache() throws Exception {
        write("dep_lib.py", "def tag():\n"
                + "    return 'one'\n");
        write("dep_entry.py", "from dep_lib import tag\n"
                + "value = tag()\n");

        Value before = asValue(host.loadEntry("./server_scripts/src/dep_entry.py"));
        assertEquals("one", before.getMember("value").asString());

        Files.writeString(paths.serverScripts().resolve("src/dep_lib.py"),
                "def tag():\n    return 'two'\n");
        host.invalidateModuleTree("./server_scripts/src/dep_entry.py");

        Value after = asValue(host.loadEntry("./server_scripts/src/dep_entry.py"));
        assertEquals("two", after.getMember("value").asString(),
                "a changed Python dependency must invalidate through the shared cache, not a second resolver");
    }

    /**
     * AC5 的**自动**失效路径：不调用 {@code invalidateModuleTree}／{@code invalidate}，
     * 仅改动被 import 的 .py 后重跑入口。宿主的 {@code refreshPreparedExecutionTree} 必须在
     * 入口缓存命中之前发现子模块的 prepared key 变化并使之失效。
     *
     * <p>与 {@code changedPythonDependencyIsReloadedThroughTheSharedCache}（显式
     * {@code invalidateModuleTree}）互补：那条证明显式失效，这条证明依赖驱动失效。
     */
    @Test
    void changedPythonDependencyIsObservedWithoutAnExplicitInvalidate() throws Exception {
        write("auto_lib.py", "def tag():\n"
                + "    return 'auto-one'\n");
        write("auto_entry.py", "from auto_lib import tag\n"
                + "value = tag()\n");

        Value before = asValue(host.loadEntry("./server_scripts/src/auto_entry.py"));
        assertEquals("auto-one", before.getMember("value").asString());

        // 只改盘上内容，不做任何显式 invalidate / clear。
        Files.writeString(paths.serverScripts().resolve("src/auto_lib.py"),
                "def tag():\n    return 'auto-two'\n");

        Value after = asValue(host.loadEntry("./server_scripts/src/auto_entry.py"));
        assertEquals("auto-two", after.getMember("value").asString(),
                "a changed dependency must be observed by the automatic dependency-driven invalidation");
    }

    @Test
    void unchangedPythonDependencyIsNotReevaluated() throws Exception {
        write("once_lib.py", "calls = {'n': 0}\n"
                + "\n"
                + "def bump():\n"
                + "    calls['n'] = calls['n'] + 1\n"
                + "    return calls['n']\n");
        write("once_entry.py", "from once_lib import bump\n"
                + "first = bump()\n"
                + "second = bump()\n");

        Value exports = asValue(host.loadEntry("./server_scripts/src/once_entry.py"));

        assertEquals(1, exports.getMember("first").asInt(), "module state must be shared across imports");
        assertEquals(2, exports.getMember("second").asInt(),
                "the imported Python module must be evaluated once, like any other module identity");
    }

    // ---- AC6: mixed Python / JS / ESM error attribution ----

    @Test
    void pythonAndEsmModulesImportEachOtherWithObservableAttribution() throws Exception {
        write("mixed_esm.mjs", "export const tag = 'esm';\n");
        write("mixed_py.py", "import mixed_esm\n"
                + "\n"
                + "def read():\n"
                + "    return mixed_esm.tag\n"
                + "\n"
                + "seen = read()\n");

        Value fromPython = asValue(host.loadEntry("./server_scripts/src/mixed_py.py"));
        assertEquals("esm", fromPython.getMember("seen").asString(),
                "a .py module must import a .mjs module through the shared resolution path");

        write("mixed_js_entry.mjs", "import { seen } from './mixed_py.py';\n"
                + "export const value = seen;\n");
        Value fromEsm = asValue(host.loadEntry("./server_scripts/src/mixed_js_entry.mjs"));
        assertEquals("esm", fromEsm.getMember("value").asString(),
                "an .mjs module must import a .py module through the same identity model");
    }

    @Test
    void missingPythonModuleIsResolvedAtTheResolveStageNotExecution() throws Exception {
        write("missing_import.py", "from not_there import thing\n"
                + "value = 1\n");

        IOException failure = assertThrows(IOException.class,
                () -> host.loadEntry("./server_scripts/src/missing_import.py"));

        NekoModuleError staged = NekoModulePipelinePrepareTest.assertStaged(
                failure, NekoModuleError.Stage.RESOLVE, NekoModuleError.OWNER_RESOLUTION_CACHE);
        assertTrue(String.valueOf(staged.getMessage()).contains("not_there"), String.valueOf(staged));
        assertEquals("server_scripts/src/missing_import.py", staged.sourcePath().replace('\\', '/'),
                "the RESOLVE failure must name the authored importing .py module: " + staged.detail());
        assertEquals("./not_there", staged.moduleId());
    }

    @Test
    void failingEsmChildImportedFromPythonKeepsTheEsmChildIdentity() throws Exception {
        write("esm_child.mjs", "export const ok = 1;\n"
                + "throw new Error('esm-child-boom');\n");
        write("esm_outer.py", "import esm_child\n"
                + "\n"
                + "value = esm_child.ok\n");

        IOException failure = assertThrows(IOException.class,
                () -> host.loadEntry("./server_scripts/src/esm_outer.py"));

        NekoModuleError staged = NekoModulePipelinePrepareTest.assertStaged(
                failure, NekoModuleError.Stage.EXECUTE, NekoModuleError.OWNER_EXECUTION);
        assertTrue(String.valueOf(staged.getMessage()).contains("esm-child-boom"), String.valueOf(staged));
        assertEquals("server_scripts/src/esm_child.mjs", staged.sourcePath().replace('\\', '/'),
                "a failing ESM child must keep its own identity instead of being attributed to the .py importer");
    }

    // ---- helpers ----

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