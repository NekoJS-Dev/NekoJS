package com.tkisor.nekojs.core.module;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.data.ScriptId;
import com.tkisor.nekojs.core.ScriptFilePolicy;
import com.tkisor.nekojs.core.compiler.NekoModuleMode;
import com.tkisor.nekojs.core.compiler.NekoTypeScriptLanguagePlugin;
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
import graal.graalvm.polyglot.PolyglotException;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 工单 13 AC3 / AC6 / AC7：Python 的错误阶段归属、执行期 source map 回映射，以及与
 * JS/CJS/ESM/TS 混合加载时的模块归属。
 *
 * <p>观测路径与生产一致：Preparation 走 {@link NekoModulePreparation#prepare}；
 * 执行期错误走 {@link NekoScriptModuleLoaderHost#loadEntry} → {@link ScriptError}（生产错误解析入口）。
 */
class PythonModuleErrorPhaseCorpusTest {

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
        compilers.register(NekoTypeScriptLanguagePlugin.INSTANCE);
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

    private Path write(String name, String source) throws IOException {
        Path file = paths.serverScripts().resolve("language-py-phases/" + name);
        Files.createDirectories(file.getParent());
        Files.writeString(file, source);
        written.add(file);
        return file;
    }

    private ScriptError capture(String entry, String idNamespace) {
        Throwable failure = assertThrows(Throwable.class,
                () -> host.loadEntry("./server_scripts/language-py-phases/" + entry));
        return ScriptError.create(ScriptType.SERVER, ScriptId.of("rt", idNamespace), entry, failure, tracker);
    }

    private static String relative(NekoJSPaths paths, Path file) {
        return paths.root().relativize(file).toString().replace('\\', '/');
    }

    // ================= AC3：准备期错误携带原始文件 + 行列 + 阶段 =================

    /**
     * Python 源语法/转换错误必须在 <strong>Preparation 阶段</strong>浮出并携带原始 .py 文件与
     * 行列，而不是延迟成执行期无来源的 Graal 异常。
     */
    @Test
    void preparationErrorsCarryOriginalFileAndPosition() throws IOException {
        record Case(String name, String source) {}
        List<Case> cases = List.of(
                new Case("unterminated-paren.py", "value = 1\nbroken = (1 + 2\n"),
                new Case("unterminated-string.py", "value = 1\ntext = 'never closed\n"),
                new Case("unclosed-def-params.py", "value = 1\ndef f(:\n    pass\n"),
                new Case("inconsistent-dedent.py", "if True:\n    a = 1\n  b = 2\n"),
                new Case("empty-suite.py", "value = 1\ndef f():\n"));

        for (Case testCase : cases) {
            Path file = write(testCase.name(), testCase.source());
            Exception failure = assertThrows(Exception.class, () -> preparation.prepare(file),
                    testCase.name() + " 必须在准备阶段失败，而不是产出坏 JS 延迟到执行期");

            String message = collectMessages(failure);
            assertTrue(message.contains(testCase.name()),
                    testCase.name() + " 的错误必须携带原始 .py 文件路径：" + message);
            assertTrue(java.util.regex.Pattern.compile("line \\d+, col \\d+").matcher(message).find(),
                    testCase.name() + " 的错误必须携带原始行列：" + message);
            // 阶段归属：错误来自准备/转换，不是 Graal 的 SyntaxError
            assertFalse(message.contains("PolyglotException"),
                    testCase.name() + " 不得被后续 JS 位置覆盖成执行期异常：" + message);
        }
    }

    private static String collectMessages(Throwable failure) {
        StringBuilder builder = new StringBuilder();
        for (Throwable current = failure; current != null; current = current.getCause()) {
            builder.append(current.getClass().getSimpleName()).append(": ").append(current.getMessage()).append('\n');
            if (current.getCause() == current) break;
        }
        return builder.toString();
    }

    // ================= AC3：执行期异常经 source map 回到原始 .py 行列 =================

    /**
     * {@code .py} 里 {@code raise} 抛出的执行期异常必须经 source map 回到原始 .py 的
     * <strong>raise 行</strong>（不是转译产物里注入的 helper/prelude 行）。
     *
     * <p>历史缺陷：Python 产物在正文之前注入运行时 helper 与异常 prelude（约 25 行），
     * 这些注入行<strong>没有 source map 映射段</strong>；错误解析取到 prelude 的第一帧
     * （{@code class Exception extends Error {}}）后会退化成「无映射」→ 行列停在产物行号、
     * 或经行号对行号错映射到无关的原始行。本测试对 raise 在文件前部与后部两种布局都断言
     * 真实 raise 行。
     */
    @Test
    void executionErrorInPythonMapsBackToRaiseLine() throws IOException {
        Path early = write("early-raise.py",
                "A = 1\nB = 2\ndef boom(n):\n    if n > 0:\n        raise ValueError('early')\n    return 0\n");
        write("early-entry.mjs", "import { boom } from './early-raise.py';\nboom(1);\n");

        ScriptError earlyError = capture("early-entry.mjs", "py-early-raise");
        assertEquals(relative(paths, early), earlyError.getDisplayPath(),
                "执行错误必须归属到真正抛错的 .py 文件");
        assertEquals(5, earlyError.getLineNumber(), "必须回到原始 .py 中 raise 所在行");
        assertTrue(earlyError.getErrorMessage().contains("early"), "错误消息必须保留："
                + earlyError.getErrorMessage());

        // 同一份语法的 raise 挪到文件后部：产物行号随之偏移，映射必须仍然指向真实 raise 行
        Path late = write("late-raise.py",
                "A = 1\n\n\ndef helper():\n    x = 1\n    y = 2\n    z = x + y\n    raise ValueError('late')\n"
                        + "\n\nhelper()\n");
        write("late-entry.mjs", "import { helper } from './late-raise.py';\nhelper();\n");

        ScriptError lateError = capture("late-entry.mjs", "py-late-raise");
        assertEquals(relative(paths, late), lateError.getDisplayPath());
        assertEquals(8, lateError.getLineNumber(), "raise 后移不得让位置停留在产物行号或错位到别的原始行");
    }

    /** 作者自定义异常类（{@code class MyError(Exception)}）的 raise 同样回到 raise 行。 */
    @Test
    void executionErrorFromUserExceptionClassMapsToRaiseLine() throws IOException {
        Path file = write("user-exception.py",
                "class MyError(Exception):\n    pass\n\n"
                        + "def boom():\n    x = 1\n    y = 2\n    raise MyError('user-defined')\n\nboom()\n");
        write("user-exception-entry.mjs", "import './user-exception.py';\n");

        ScriptError error = capture("user-exception-entry.mjs", "py-user-exception");
        assertEquals(relative(paths, file), error.getDisplayPath());
        assertEquals(7, error.getLineNumber(),
                "用户异常类的类声明帧不得顶掉真正 raise 的行");
        assertTrue(error.getErrorMessage().contains("user-defined"));
    }

    /** 跨 import 的错误归属：TS 入口经 import 调用的 Python helper 抛错 → 归属到 .py，而非入口/TS。 */
    @Test
    void errorThroughTsImportIsAttributedToThePythonModule() throws IOException {
        Path python = write("called-by-ts.py", "def explode():\n    raise ValueError('from-python')\n");
        write("calls-python.ts",
                "import { explode } from './called-by-ts.py';\nexport function go(): void { explode(); }\n");
        write("cross-entry.mjs", "import { go } from './calls-python.ts';\ngo();\n");

        ScriptError error = capture("cross-entry.mjs", "ts-calls-python");
        assertEquals(relative(paths, python), error.getDisplayPath(),
                "错误必须归属到真正抛错的 .py 模块，而不是 TS 中间模块或入口");
        assertEquals(2, error.getLineNumber(), "必须定位到 .py 中 raise 的原始行");
    }

    // ================= AC6：与 JS/CJS/ESM/TS 混合加载 =================

    /** 一个 ESM 入口同时 import .py / .js / .cjs / .mjs / .ts，各自的身份必须可追踪、执行不串味。 */
    @Test
    void mixedImportsTrackIdentityAcrossPythonAndJsFamily() throws IOException {
        write("mixed-helper.py", "def label():\n    return 'py'\n");
        write("mixed-plain.js", "module.exports = { label: () => 'js' };\n");
        write("mixed.cjs", "module.exports = { label: () => 'cjs' };\n");
        write("mixed.mjs", "export const label = () => 'mjs';\n");
        write("mixed.ts", "export const label = (): string => 'ts';\n");
        write("mixed-entry.mjs", """
                import { label as pyLabel } from './mixed-helper.py';
                import jsDefault from './mixed-plain.js';
                import cjsDefault from './mixed.cjs';
                import { label as mjsLabel } from './mixed.mjs';
                import { label as tsLabel } from './mixed.ts';
                globalThis.mixed = [pyLabel(), jsDefault.label(), cjsDefault.label(), mjsLabel(), tsLabel()].join('|');
                """);

        assertNotNull(host.loadEntry("./server_scripts/language-py-phases/mixed-entry.mjs"));
        assertEquals("py|js|cjs|mjs|ts",
                context.getBindings("js").getMember("mixed").asString(),
                "五种扩展名混合 import 必须各自执行且不串味");

        // 身份可追踪：每个文件的 languageId/mode 组合与语言注册表一致
        assertEquals("python", preparation.prepare(
                paths.serverScripts().resolve("language-py-phases/mixed-helper.py")).languageId());
        assertEquals("typescript", preparation.prepare(
                paths.serverScripts().resolve("language-py-phases/mixed.ts")).languageId());
        assertEquals("javascript", preparation.prepare(
                paths.serverScripts().resolve("language-py-phases/mixed.mjs")).languageId());
        assertEquals(NekoModuleMode.COMMONJS, preparation.prepare(
                paths.serverScripts().resolve("language-py-phases/mixed.cjs")).mode());
        assertEquals(NekoModuleMode.ESM, preparation.prepare(
                paths.serverScripts().resolve("language-py-phases/mixed-helper.py")).mode(),
                "带顶层绑定（自动 export 块）的 .py 是 ESM");
    }

    /**
     * 非法顶层控制流（{@code return} / {@code break} / {@code yield} 出现在模块顶层）在 Python 里
     * 是语法错误，本引擎的 v1 子集<strong>不在准备阶段拦截</strong>：产物是合法形状的 JS，但该语句
     * 在模块顶层不合法。此处 characterization 其真实后果——<strong>加载期硬失败且位置仍指向原 .py 行</strong>，
     * 不静默通过、不吞掉后续代码（后续语句仍在产物里）。
     */
    @Test
    void invalidTopLevelControlFlowFailsVisiblyAndKeepsFollowingCode() throws IOException {
        record Case(String name, String source, int expectedLine) {}
        List<Case> cases = List.of(
                new Case("top-return.py", "return 5\nFOLLOWING = 1\n", 1),
                new Case("top-break.py", "break\nFOLLOWING = 1\n", 1),
                new Case("nested-top-return.py", "if True:\n    return 1\nFOLLOWING = 1\n", 2));

        for (Case testCase : cases) {
            Path file = write(testCase.name(), testCase.source());
            NekoPreparedModule prepared = preparation.prepare(file);
            assertTrue(prepared.code().contains("FOLLOWING"),
                    testCase.name() + " 的准备产物不得吞掉后续代码：\n" + prepared.code());

            String entry = testCase.name().replace(".py", "") + "-entry.mjs";
            write(entry, "import './" + testCase.name() + "';\n");
            ScriptError error = capture(entry, testCase.name());
            assertEquals(relative(paths, file), error.getDisplayPath(),
                    testCase.name() + " 的加载失败必须归属到原 .py 文件");
            assertEquals(testCase.expectedLine, error.getLineNumber(),
                    testCase.name() + " 必须指向原 .py 中非法语句所在行");
        }
    }

    /** 准备产物必须是可解析的 JS：坏语句不得在转译阶段被静默丢弃或吞掉后续代码。 */
    @Test
    void preparationNeverSilentlyDropsFollowingStatements() throws IOException {
        Path file = write("no-drop.py",
                "def f():\n    return 1\n\nclass C:\n    x = 1\n\nLEADING = f()\nTRAILING = LEADING + 1\n");
        NekoPreparedModule prepared = preparation.prepare(file);

        assertTrue(prepared.code().contains("LEADING"), "首个绑定必须在产物里：\n" + prepared.code());
        assertTrue(prepared.code().contains("TRAILING"), "末个绑定必须在产物里：\n" + prepared.code());
        assertTrue(prepared.code().contains("function f"), "函数定义必须在产物里");
        assertTrue(prepared.code().contains("class C"), "类定义必须在产物里");
    }

    /** 执行期异常不得被吞成无来源的 Graal 异常：原始异常必须能从执行入口观察到。 */
    @Test
    void pythonExecutionFailureSurfacesFromLoadEntryWithOriginalCause() throws IOException {
        write("surfacing.py", "def boom():\n    raise ValueError('surfaced')\n\nboom()\n");

        Throwable failure = assertThrows(Throwable.class,
                () -> host.loadEntry("./server_scripts/language-py-phases/surfacing.py"));

        PolyglotException polyglot = null;
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof PolyglotException candidate) {
                polyglot = candidate;
                break;
            }
        }
        assertNotNull(polyglot, "执行失败必须保留 Graal 异常（不得被换成无来源的 IOException）：" + failure);
        assertTrue(String.valueOf(polyglot.getMessage()).contains("surfaced"),
                "原始错误消息必须保留：" + polyglot.getMessage());
    }
}
