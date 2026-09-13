package com.tkisor.nekojs.core.module;

import com.tkisor.nekojs.core.compiler.NekoJavaScriptLanguagePlugin;
import com.tkisor.nekojs.core.compiler.NekoJsxLanguagePlugin;
import com.tkisor.nekojs.core.compiler.NekoModuleMode;
import com.tkisor.nekojs.core.compiler.NekoTypeScriptLanguagePlugin;
import com.tkisor.nekojs.core.compiler.ScriptCompilerRegistry;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.error.SourceMapRegistry;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.testfixture.TestModulePreparation;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.HostAccess;
import graal.graalvm.polyglot.Value;
import graal.graalvm.polyglot.io.IOAccess;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 工单 12 AC1 / AC2 / AC6：{@code .ts} / {@code .jsx} / {@code .tsx} 的 representative corpus
 * 从最高调用者 Seam（{@link NekoModulePreparation#prepare} 与 {@code NekoModulePipeline.prepare}）
 * 观察，覆盖：
 * <ul>
 *   <li><strong>AC1</strong>：正确 language id、module mode、<strong>真正可执行</strong>的 code、
 *       <strong>非空且可用</strong>的 source map；</li>
 *   <li><strong>AC2</strong>：类型擦除不改变运行时值与导出形状（用执行结果固定，不用私有 AST 布局）；</li>
 *   <li><strong>AC6</strong>：内容 / language id 变化使 cache 失效；未变化可观察命中，
 *       {@code .tsx} 在缺陷 A 修好后其身份变化能正确失效。</li>
 * </ul>
 */
class NekoLanguagePipelineCorpusTest {

    /** representative corpus：每种扩展名一份「有类型/JSX 语法但运行时行为固定」的源。 */
    private record Representative(String extension, String source, String name, int expectedResult, int expectedSourceLine) {}

    private static final List<Representative> CORPUS = List.of(
            // .ts：类型擦除后 area(6,7)=42 加 id.length=1 → 43；结果语句在第 5 行
            new Representative(".ts", """
                    interface Point { x: number; y: number }
                    type Id = string | number;
                    function area(p: Point): number { return p.x * p.y }
                    const id: Id = 'k';
                    globalThis.result = area({ x: 6, y: 7 }) + id.length;
                    """, "ts-types-erased", 43, 5),
            // .jsx：元素子节点 1 + 1 = 2；结果语句在第 2 行
            new Representative(".jsx", """
                    const el = <div>{1 + 1}</div>;
                    globalThis.result = el.children[0];
                    """, "jsx-element", 2, 2),
            // .tsx：类型擦除 + JSX lowering 组合；box.value = 2；结果语句在第 4 行
            new Representative(".tsx", """
                    interface Box { value: number }
                    const box: Box = { value: 2 };
                    const el = <div>{box.value}</div>;
                    globalThis.result = el.children[0];
                    """, "tsx-element-with-types", 2, 4));

    private NekoJSPaths paths;
    private ScriptCompilerRegistry compilers;
    private NekoModulePreparation preparation;
    private Context context;
    private final List<Path> written = new ArrayList<>();

    @BeforeAll
    static void initPlatform() {
        TestPlatformInit.ensureInitialized();
    }

    @BeforeEach
    void setUp() {
        paths = NekoJSPaths.get();
        compilers = ScriptCompilerRegistry.createRuntimeRegistry();
        compilers.register(NekoJavaScriptLanguagePlugin.INSTANCE);
        compilers.register(NekoTypeScriptLanguagePlugin.INSTANCE);
        compilers.register(NekoJsxLanguagePlugin.INSTANCE);
        preparation = TestModulePreparation.create(paths, compilers, SandboxConfig.defaultConfig());
        context = Context.newBuilder("js")
                .allowHostAccess(HostAccess.NONE)
                .allowHostClassLookup(className -> false)
                .allowIO(IOAccess.NONE)
                .build();
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
        Path file = paths.serverScripts().resolve("language-corpus/" + name);
        Files.createDirectories(file.getParent());
        Files.writeString(file, source);
        written.add(file);
        return file;
    }

    /** 经典 JSX runtime 的最小 factory/fragment shim：元素形状与生产 define.js 的 createJsxElement 同构。 */
    private static final String JSX_SHIM = """
            globalThis.__nekoJsxFactory = (type, props, ...children) => ({ type, props, children });
            globalThis.__nekoJsxFragment = (...children) => ({ type: null, props: null, children });
            """;

    /**
     * 在同一个 Context 里执行一段模块产物。用 IIFE 包起来：corpus 的多个条目会声明同名
     * 顶层绑定（如 {@code const el}），直接连续 eval 会撞 "already declared"。
     */
    private Value execute(String code) {
        context.eval("js", JSX_SHIM + "(function () {\n" + code + "\n})();\nglobalThis.result");
        return context.getBindings("js").getMember("result");
    }

    // ---- AC1 ----

    @Test
    void representativeCorpusProducesLanguageIdModeExecutableCodeAndUsableSourceMap() throws Exception {
        for (Representative representative : CORPUS) {
            String name = representative.name() + representative.extension();
            Path file = write(name, representative.source());
            NekoPreparedModule prepared = preparation.prepare(file);

            String expectedLanguage = switch (representative.extension()) {
                case ".ts" -> "typescript";
                case ".jsx", ".tsx" -> "jsx";
                default -> throw new IllegalStateException(representative.extension());
            };
            assertEquals(expectedLanguage, prepared.languageId(),
                    name + " 的 language id 必须与语言注册表一致");
            assertEquals(NekoModuleMode.AUTO, prepared.requestedMode(),
                    name + " 的 .ts/.jsx/.tsx 请求模式是 AUTO");
            assertEquals(NekoModuleMode.COMMONJS, prepared.mode(),
                    name + " 无 import/export → 解析为 COMMONJS");

            // 可执行 code：真的跑起来，产出预期值（不是只断言 contains）
            assertEquals(representative.expectedResult(), execute(prepared.code()).asInt(),
                    name + " 的产物必须可执行且行为正确：\n" + prepared.code());

            // 非空 source map（TS/JSX/TSX 都是转译语言，必须有真实映射）
            assertNotNull(prepared.sourceMap(), name + " 必须产出非空 source map");

            // 可用 source map：注册后能把产物行映射回原始源文件的原始行
            String relative = paths.root().relativize(file).toString().replace('\\', '/');
            SourceMapRegistry.register(relative, prepared.sourceMap(), prepared.prependedLineCount());
            int generatedLine = lineOf(prepared.code(), "globalThis.result");
            SourceMapRegistry.OriginalPosition mapped =
                    SourceMapRegistry.getMappedPosition(relative, generatedLine, 1);
            assertNotNull(mapped.path, name + " 的 source map 必须能映射回原始源路径");
            assertEquals(relative, mapped.path, name + " 必须映射回同一原始文件");
            assertEquals(representative.expectedSourceLine(), mapped.line,
                    name + " 必须映射回原始源中 globalThis.result 所在的行");
        }
    }

    private static int lineOf(String text, String needle) {
        String[] lines = text.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].contains(needle)) {
                return i + 1;
            }
        }
        throw new AssertionError("not found: " + needle + " in\n" + text);
    }

    // ---- AC2：擦除不改变运行时值 / 控制流 / 导出形状 ----

    @Test
    void typescriptErasureKeepsControlFlowValuesAndExportShape() throws Exception {
        Path file = write("export-shape.ts", """
                export type Config = { enabled: boolean };
                export interface Named { name: string }
                const internal: number = 1;
                export const answer: number = 40 + internal;
                export function label(n: number): string {
                  return n > 0 ? 'pos' : 'neg';
                }
                export { internal as renamed };
                """);
        NekoPreparedModule prepared = preparation.prepare(file);

        assertEquals(NekoModuleMode.ESM, prepared.mode(), "带 export 的 .ts 是 ESM");
        assertNotNull(prepared.esmAst(), "ESM 模块必须携带 AST 供 link 使用");

        // 导出形状：擦除类型不得新增/删除运行时导出
        String code = prepared.code();
        assertFalse(code.contains("export type"), "类型别名导出必须被擦除：\n" + code);
        assertFalse(code.contains("export interface"), "interface 导出必须被擦除：\n" + code);
        assertTrue(code.contains("export const answer"), "值导出必须保留：\n" + code);
        assertTrue(code.contains("export function label"), "函数导出必须保留：\n" + code);
        assertTrue(code.contains("renamed"), "重命名导出必须保留：\n" + code);
        assertEquals(3, prepared.esmAst().exports().size(),
                "运行时导出必须恰好 3 个（answer / label / renamed），类型导出不计入");

        // 控制流与运行时值：把 export 关键字去掉后在普通上下文里真跑一遍模块体，
        // 确认三元与算术语义未被类型擦除破坏（不依赖私有 AST 布局）。
        context.eval("js", stripExports(code) + "\nglobalThis.__check = label(1) + ':' + answer");
        assertEquals("pos:41", context.getBindings("js").getMember("__check").asString(),
                "擦除不得改变控制流与运行时值：\n" + code);
    }

    /**
     * 去掉产物里的 {@code export} 关键字，得到等价的可直接求值的模块体。
     * 纯测试辅助：只为在同一个 Context 里观察运行时值与控制流。
     */
    private static String stripExports(String code) {
        StringBuilder out = new StringBuilder();
        for (String line : code.split("\n", -1)) {
            String trimmed = line.trim();
            if (trimmed.startsWith("export {")) {
                continue; // 重命名导出只有形状意义，运行体里没有对应语句
            }
            out.append(line.replaceFirst("^(\\s*)export\\s+(?=(const|let|var|function|class)\\b)", "$1"))
                    .append('\n');
        }
        return out.toString();
    }

    // ---- AC6：cache invalidation ----

    @Test
    void tsxContentChangeInvalidatesCacheAndLanguageIdentityChangeIsObservable() throws Exception {
        Path file = write("cache.tsx", "const el = <div>{1}</div>;\n");
        NekoPreparedModule first = preparation.prepare(file);

        assertTrue(first == preparation.prepare(file), "未变化必须命中同一 prepared module 实例");

        Files.writeString(file, "const el = <div>{2}</div>;\n");
        NekoPreparedModule afterEdit = preparation.prepare(file);
        assertNotSame(first, afterEdit, "内容变化必须失效缓存");
        assertFalse(afterEdit.cacheKey().equals(first.cacheKey()), "内容变化必须改变 cache key");

        // 同路径同内容，但该扩展名换了语言：identity 变化必须失效（缺陷 A 修好后 .tsx 的
        // identity 来自插件 id，换语言时 key 必须跟着变）
        Path same = write("identity.tsx", "const el = <div>{3}</div>;\n");
        NekoPreparedModule jsxOwned = preparation.prepare(same);

        ScriptCompilerRegistry replaced = ScriptCompilerRegistry.createRuntimeRegistry();
        replaced.register(NekoJavaScriptLanguagePlugin.INSTANCE);
        replaced.register(NekoTypeScriptLanguagePlugin.INSTANCE);
        replaced.registerLanguage("customtsx", java.util.Set.of(".tsx"), new com.tkisor.nekojs.core.compiler.IScriptCompiler() {
            @Override public boolean canCompile(String extension) { return true; }
            @Override public String compile(Path path, String source) { return "globalThis.result = 'custom';\n"; }
        });
        NekoModulePreparation other = TestModulePreparation.create(paths, replaced, SandboxConfig.defaultConfig());
        NekoPreparedModule custom = other.prepare(same);

        assertEquals("customtsx", custom.languageId(), "换语言后 language id 必须变化");
        assertFalse(jsxOwned.cacheKey().equals(custom.cacheKey()),
                "language id 必须进入 cache key，否则会返回旧语言编译的模块");
        assertTrue(custom.code().contains("custom"), "失效后必须用新语言重新编译");
    }

    /** .jsx 与 .tsx 的产物必须是同一 JSX 语言身份：两层的 languageId 必须相等（缺陷 A 的不变量）。 */
    @Test
    void jsxAndTsxShareTheSameLanguageIdentityAcrossLayers() throws Exception {
        Path jsx = write("identity.jsx", "const el = <div>{1}</div>;\n");
        Path tsx = write("identity2.tsx", "const n: number = 1;\nconst el = <div>{n}</div>;\n");

        NekoPreparedModule jsxModule = preparation.prepare(jsx);
        NekoPreparedModule tsxModule = preparation.prepare(tsx);

        assertEquals(jsxModule.languageId(), tsxModule.languageId(),
                ".jsx 与 .tsx 属于同一 JSX 语言族，language id 必须一致");
        assertEquals(preparation.pipeline().identityOf(jsx).languageId(),
                preparation.pipeline().identityOf(tsx).languageId(),
                "identityOf（缓存命中预判）也必须给出同一个 language id");
    }
}
