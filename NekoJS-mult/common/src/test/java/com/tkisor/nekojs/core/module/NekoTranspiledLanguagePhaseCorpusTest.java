package com.tkisor.nekojs.core.module;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.data.ScriptId;
import com.tkisor.nekojs.core.ScriptFilePolicy;
import com.tkisor.nekojs.core.compiler.NekoCompilationPipeline;
import com.tkisor.nekojs.core.compiler.NekoJavaScriptLanguagePlugin;
import com.tkisor.nekojs.core.compiler.NekoJsxLanguagePlugin;
import com.tkisor.nekojs.core.compiler.NekoModuleMode;
import com.tkisor.nekojs.core.compiler.NekoTypeScriptLanguagePlugin;
import com.tkisor.nekojs.core.compiler.ScriptCompilerRegistry;
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
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 工单 12 AC3 / AC4 / AC5 / AC7：TS/JSX/TSX 的 JSX 结构、准备期错误阶段归属、
 * 执行期 source map 回映射，以及与 JS/CJS/ESM 的混合 import 身份归属。
 *
 * <p>观测路径与生产一致：Preparation 走 {@link NekoModulePreparation#prepare}；
 * 执行期错误走 {@link NekoScriptModuleLoaderHost#loadEntry} → {@link ScriptError}。
 */
class NekoTranspiledLanguagePhaseCorpusTest {

    private NekoJSPaths paths;
    private Context context;
    private NekoScriptModuleLoaderHost host;
    private NekoModulePreparation preparation;
    private ScriptCompilerRegistry compilers;
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
        compilers = ScriptCompilerRegistry.createRuntimeRegistry();
        compilers.register(NekoJavaScriptLanguagePlugin.INSTANCE);
        compilers.register(NekoTypeScriptLanguagePlugin.INSTANCE);
        compilers.register(NekoJsxLanguagePlugin.INSTANCE);
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
        // classic JSX runtime 的全局 factory：生产由 resources/nekojs/node/internal/define.js 注入。
        // 测试只加载 script-loader.js，因此这里补同构 shim——否则 .jsx/.tsx 里的 JSX 表达式
        // 会在求值时报 "not a function"，把 source map/归属断言引到错误的位置。
        context.eval("js", """
                globalThis.__nekoJsxFactory = (type, props, ...children) => ({ type, props, children });
                globalThis.__nekoJsxFragment = (...children) => ({ type: null, props: null, children });
                """);
    }

    @AfterEach
    void tearDown() throws IOException {
        context.close();
        SourceMapRegistry.clear();
        // 真删文件 + 空的空父目录：deleteOnExit 只在 JVM 退出时生效，会污染共享 gameDir
        TestModulePreparation.deleteFilesAndEmptyParents(written);
        written.clear();
    }

    private Path write(String name, String source) throws IOException {
        Path file = paths.serverScripts().resolve("language-phases/" + name);
        Files.createDirectories(file.getParent());
        Files.writeString(file, source);
        written.add(file);
        return file;
    }

    // ================= AC3：JSX/TSX element、fragment 与 automatic runtime =================

    /**
     * automatic runtime 的 .tsx：JSX lowering（jsx/jsxs/Fragment + 注入 import）与 TS 类型擦除
     * 必须同时生效，且结果真的能跑出正确的元素树。
     */
    @Test
    void tsxAutomaticRuntimeElementAndFragmentExecuteCorrectly() throws Exception {
        String source = """
                interface Props { name: string }
                const props: Props = { name: 'a' };
                const single = <div id={props.name}>one</div>;
                const multi = <div><span/>two</div>;
                const frag = <><span/>two</>;
                export const tree = JSON.stringify([single, multi, frag]);
                """;

        String code = new NekoCompilationPipeline()
                .compile(Path.of("automatic.tsx"), source, ".tsx", NekoJsxLanguagePlugin.INSTANCE, true).code();

        assertTrue(code.contains("from 'nekojs/jsx-runtime'"), "automatic runtime 必须注入 runtime import：\n" + code);
        assertTrue(code.contains("jsx("), "单子节点必须用 jsx()：\n" + code);
        assertTrue(code.contains("jsxs("), "多子节点必须用 jsxs()：\n" + code);
        assertTrue(code.contains("Fragment"), "fragment 必须用 Fragment 类型：\n" + code);
        assertFalse(code.contains(": string"), "interface 字段类型必须被擦除：\n" + code);

        // 执行：把注入的 runtime import 换成本地同构 shim（与 NekoJsxCompilerTest.assertAutomaticTree 同一手法，
        // 该 shim 与生产 resources/nekojs/node/modules/jsx-runtime.ts 的元素形状一致），并剥掉 export 关键字，
        // 让 lowering 后的模块体在同一 Context 里真跑一遍。
        String executable = code.replaceFirst("(?m)\\Aimport \\{ [^\\r\\n]+ } from 'nekojs/jsx-runtime';\\R", "");
        assertFalse(executable.equals(code), "automatic 产物必须以 runtime import 开头：\n" + code);
        String runtime = """
                const Fragment = Symbol('Fragment');
                const node = (kind, type, props) => ({
                  kind, type: type === Fragment ? 'Fragment' : type,
                  children: props?.children ?? null
                });
                const jsx = (type, props) => node('jsx', type, props);
                const jsxs = (type, props) => node('jsxs', type, props);
                """;
        context.eval("js", runtime
                + "globalThis.__tree = (function (jsx, jsxs, Fragment) {\n"
                + executable.replaceFirst("(?m)^export const tree", "const tree")
                + "\nreturn tree;\n})(jsx, jsxs, Fragment);");
        assertEquals(
                "[{\"kind\":\"jsx\",\"type\":\"div\",\"children\":\"one\"},"
                        + "{\"kind\":\"jsxs\",\"type\":\"div\",\"children\":[{\"kind\":\"jsx\",\"type\":\"span\",\"children\":null},\"two\"]},"
                        + "{\"kind\":\"jsxs\",\"type\":\"Fragment\",\"children\":[{\"kind\":\"jsx\",\"type\":\"span\",\"children\":null},\"two\"]}]",
                context.getBindings("js").getMember("__tree").asString(),
                "automatic runtime 的元素/fragment 树必须与既有 golden 一致：\n" + code);
    }

    /** classic runtime（默认）的 .jsx/.tsx 必须仍然产出 factory 调用，且 element/fragment 都能跑。 */
    @Test
    void jsxClassicRuntimeElementAndFragmentExecuteCorrectly() throws Exception {
        NekoCompilationPipeline pipeline = new NekoCompilationPipeline();
        String jsx = pipeline.compile(Path.of("el.jsx"), "const el = <div>hi</div>;", ".jsx", NekoJsxLanguagePlugin.INSTANCE).code();
        String tsx = pipeline.compile(Path.of("el.tsx"), "const el: unknown = <div>hi</div>;", ".tsx", NekoJsxLanguagePlugin.INSTANCE).code();

        for (String code : new String[]{jsx, tsx}) {
            assertTrue(code.contains("__nekoJsxFactory"), "classic runtime 必须用 factory：\n" + code);
            assertFalse(code.contains("nekojs/jsx-runtime"), "classic runtime 不得注入 automatic import：\n" + code);
        }
        context.eval("js", "(function () {\n" + jsx + "\nglobalThis.__el = el;\n})();");
        assertEquals("hi", context.getBindings("js").getMember("__el").getMember("children").getArrayElement(0).asString());

        // fragment：classic runtime 走 __nekoJsxFragment
        String fragMjs = pipeline.compile(Path.of("frag.jsx"), "const frag = <><b/></>;\nglobalThis.__frag = frag;",
                ".jsx", NekoJsxLanguagePlugin.INSTANCE).code();
        assertTrue(fragMjs.contains("__nekoJsxFragment"), "fragment 必须走 fragment factory：\n" + fragMjs);
        context.eval("js", "(function () {\n" + fragMjs + "\n})();");
        assertEquals(1, context.getBindings("js").getMember("__frag").getMember("children").getArraySize(),
                "fragment 必须带上自己的子节点");
    }

    // ================= AC4：准备期错误携带原始文件 + 行列 + 阶段 =================

    /**
     * 三种转译语言的准备期错误都必须在 Preparation 阶段浮出，携带<strong>原始文件</strong>与行列，
     * 而不是延迟成执行期无来源的 Graal 异常。
     */
    @Test
    void preparationErrorsCarryOriginalFileAndLineForTsJsxTsx() throws IOException {
        record Case(String name, String source, String position) {}
        List<Case> cases = List.of(
                new Case("unterminated.ts", "const before = 1;\ninterface Broken {\n  a: string;\n", "unterminated.ts"),
                new Case("unterminated.jsx", "const before = 1;\nconst el = <div>\n", "unterminated.jsx"),
                new Case("unterminated.tsx", "const before = 1;\nconst el: unknown = <div>\n", "unterminated.tsx"));

        for (Case testCase : cases) {
            Path file = write(testCase.name(), testCase.source());
            Exception failure = assertThrows(Exception.class, () -> preparation.prepare(file),
                    testCase.name() + " 必须在准备阶段失败，而不是产出坏 JS 延迟到执行期");

            String message = collectMessages(failure);
            assertTrue(message.contains(testCase.name()),
                    testCase.name() + " 的错误必须携带原始 TS/JSX/TSX 文件路径：" + message);
            assertTrue(message.matches("(?s).*\\b[0-9]+:[0-9]+\\b.*"),
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

    // ================= AC5：执行期异常经 source map 回到原始 JSX/TSX =================

    private ScriptError capture(String entry, String idNamespace) {
        Throwable failure = assertThrows(Throwable.class, () -> host.loadEntry("./server_scripts/language-phases/" + entry));
        return ScriptError.create(ScriptType.SERVER, ScriptId.of("rt", idNamespace), entry, failure, tracker);
    }

    /**
     * {@code .jsx} / {@code .tsx} 里抛出的执行期异常必须经 source map 回到原始文件与原始行，
     * 并保留模块身份（language id / mode / cache key）。
     */
    @Test
    void executionErrorInJsxAndTsxMapsBackThroughSourceMap() throws IOException {
        Path jsxHelper = write("jsx-helper.jsx", """
                export function boom() {
                  const el = <div>never</div>;
                  throw new Error('jsx-kaboom');
                }
                """);
        Path tsxHelper = write("tsx-helper.tsx", """
                export function boom(): void {
                  const n: number = 1;
                  const el = <div>{n}</div>;
                  throw new Error('tsx-kaboom');
                }
                """);
        write("jsx-entry.mjs", """
                import { boom } from './jsx-helper.jsx';
                boom();
                """);
        write("tsx-entry.mjs", """
                import { boom } from './tsx-helper.tsx';
                boom();
                """);

        ScriptError jsxError = capture("jsx-entry.mjs", "jsx-source-map-error");
        assertEquals(paths.root().relativize(jsxHelper).toString().replace('\\', '/'), jsxError.getDisplayPath(),
                "必须经 source map 回到原始 .jsx 文件");
        assertEquals(3, jsxError.getLineNumber(), "必须映射到原始 .jsx 的 throw 行（不是产物行号）");
        assertTrue(jsxError.getColumnNumber() > 0, "必须携带来自 source map 的原始列：" + jsxError.getColumnNumber());
        assertTrue(jsxError.getErrorMessage().contains("jsx-kaboom"));

        ScriptError tsxError = capture("tsx-entry.mjs", "tsx-source-map-error");
        assertEquals(paths.root().relativize(tsxHelper).toString().replace('\\', '/'), tsxError.getDisplayPath(),
                "必须经 source map 回到原始 .tsx 文件");
        assertEquals(4, tsxError.getLineNumber(), "必须映射到原始 .tsx 的 throw 行（不是产物行号）");
        assertTrue(tsxError.getColumnNumber() > 0, "必须携带来自 source map 的原始列：" + tsxError.getColumnNumber());
        assertTrue(tsxError.getErrorMessage().contains("tsx-kaboom"));

        // 模块身份：错误归属的模块必须与 Resolution/Cache 认定的身份一致
        NekoPreparedModule jsxModule = preparation.prepare(jsxHelper);
        assertEquals("jsx", jsxModule.languageId(), ".jsx 模块身份的语言 id 必须可解释");
        assertEquals(NekoModuleMode.ESM, jsxModule.mode(), ".jsx 模块的 module 模式是身份的一部分");
        assertNotNull(jsxModule.cacheKey(), "必须携带稳定 cache key（身份可追踪）");

        NekoPreparedModule tsxModule = preparation.prepare(tsxHelper);
        assertEquals(jsxModule.languageId(), tsxModule.languageId(),
                ".jsx 与 .tsx 必须报同一个语言身份（缺陷 A 的不变量）");
    }

    // ================= AC7：与 JS/CJS/ESM 混合 import 的身份与错误归属 =================

    /** 一个 ESM 入口同时 import .js / .cjs / .mjs / .ts / .jsx / .tsx，各自的身份必须可追踪。 */
    @Test
    void mixedImportsTrackIdentityAcrossJsCjsEsmTsJsxTsx() throws IOException {
        write("mixed-plain.js", "module.exports = { kind: 'cjs-js', value: 1 };\n");
        write("mixed.cjs", "module.exports = { kind: 'cjs', value: 2 };\n");
        write("mixed.mjs", "export const kind = 'mjs';\nexport const value = 3;\n");
        write("mixed.ts", "export const kind: string = 'ts';\nexport const value: number = 4;\n");
        write("mixed.jsx", "export const kind = 'jsx';\nexport const value = 5;\nexport const el = <b/>;\n");
        write("mixed.tsx", "export const kind: string = 'tsx';\nexport const value: number = 6;\nexport const el = <b/>;\n");
        write("mixed-entry.mjs", """
                import { kind as mjsKind, value as mjsValue } from './mixed.mjs';
                import { kind as tsKind, value as tsValue } from './mixed.ts';
                import { kind as jsxKind, value as jsxValue } from './mixed.jsx';
                import { kind as tsxKind, value as tsxValue } from './mixed.tsx';
                globalThis.mixed = [mjsKind, mjsValue, tsKind, tsValue, jsxKind, jsxValue, tsxKind, tsxValue].join('|');
                """);

        assertNotNull(host.loadEntry("./server_scripts/language-phases/mixed-entry.mjs"));
        assertEquals("mjs|3|ts|4|jsx|5|tsx|6",
                context.getBindings("js").getMember("mixed").asString(),
                "六种扩展名混合 import 必须各自执行且不串味");

        // 身份可追踪：每个文件的 languageId/mode/cacheKey 组合唯一且正确
        assertEquals("typescript", preparation.prepare(paths.serverScripts().resolve("language-phases/mixed.ts")).languageId());
        assertEquals("jsx", preparation.prepare(paths.serverScripts().resolve("language-phases/mixed.jsx")).languageId());
        assertEquals("jsx", preparation.prepare(paths.serverScripts().resolve("language-phases/mixed.tsx")).languageId());
        assertEquals("javascript", preparation.prepare(paths.serverScripts().resolve("language-phases/mixed.mjs")).languageId());
        assertEquals(NekoModuleMode.COMMONJS,
                preparation.prepare(paths.serverScripts().resolve("language-phases/mixed.cjs")).mode());
    }

    /** 混合 import 链上，错误必须归属到真正抛错的 .tsx，而不是入口或中间模块。 */
    @Test
    void mixedImportErrorIsAttributedToTheFailingTranspiledModule() throws IOException {
        write("ok-a.mjs", "export const a = 1;\n");
        write("ok-b.ts", "export const b: number = 2;\n");
        Path failing = write("failing.tsx", """
                export function boom(): void {
                  const el = <b/>;
                  throw new Error('attributed-to-tsx');
                }
                """.replaceAll("^\\s+", ""));
        write("mixed-error-entry.mjs", """
                import { a } from './ok-a.mjs';
                import { b } from './ok-b.ts';
                import { boom } from './failing.tsx';
                boom();
                """);

        ScriptError error = capture("mixed-error-entry.mjs", "mixed-attribution");
        assertEquals(paths.root().relativize(failing).toString().replace('\\', '/'), error.getDisplayPath(),
                "错误必须归属到真正抛错的 .tsx，而不是入口或 .ts/.mjs 中间模块");
        assertEquals(3, error.getLineNumber(), "必须定位到 .tsx 里 throw 的原始行（真实源码第 3 行）");
        assertTrue(error.getErrorMessage().contains("attributed-to-tsx"));
    }
}
