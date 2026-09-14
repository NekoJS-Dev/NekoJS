package com.tkisor.nekojs.core.module;

import com.tkisor.nekojs.core.compiler.NekoModuleMode;
import com.tkisor.nekojs.core.compiler.ScriptCompilerRegistry;
import com.tkisor.nekojs.core.compiler.python.PythonToJsCompiler;
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
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 工单 13 AC1 / AC2 / AC4 / AC5：{@code .py} 的 representative corpus 与身份/缓存一致性。
 *
 * <p>观测 Seam 与生产一致：
 * <ul>
 *   <li>Preparation 走 {@link NekoModulePreparation#prepare}（runtime owner 持有的实例）；</li>
 *   <li>可执行 code 真的在 GraalJS 里跑出预期值（不是只断言 {@code contains}）；</li>
 *   <li>source map 经 {@link SourceMapRegistry} 查表验证可用；</li>
 *   <li>cache 命中/失效通过实例身份 + cache key 观察。</li>
 * </ul>
 *
 * <p><strong>corpus 只固定有意承诺的行为</strong>（language id / mode / 执行结果 / 映射行），
 * 不冻结 {@code PythonNode} / {@code PythonEmitter} 的私有对象身份或产物全文——转译器内部结构
 * 可以继续演进，只要这份可观察契约不变。
 */
class PythonModulePipelineCorpusTest {

    /** representative corpus：覆盖缩进、定义/类/调用、注释、导入。 */
    private record Representative(String name, String source, int expectedResult, int expectedSourceLine) {}

    private static final List<Representative> CORPUS = List.of(
            // 缩进 + 定义 + 调用：elif 链与 while/for 缩进结构
            new Representative("indent-def-call", """
                    # 缩进与定义：fizz 分类器
                    def classify(n):
                        if n % 15 == 0:
                            return 'fizzbuzz'
                        elif n % 3 == 0:
                            return 'fizz'
                        elif n % 5 == 0:
                            return 'buzz'
                        return str(n)

                    total = 0
                    for i in range(1, 16):
                        if classify(i) == 'fizzbuzz':
                            total += 1

                    globalThis.result = total
                    """, 1, 16),
            // 类 + 继承 + 方法调用：account 余额
            new Representative("class-hierarchy", """
                    class Account:
                        def __init__(self, owner, balance=0):
                            self.owner = owner
                            self.balance = balance

                        def deposit(self, amount):
                            self.balance += amount
                            return self.balance

                    acc = Account("neko", 100)
                    acc.deposit(20)

                    globalThis.result = acc.balance
                    """, 120, 13),
            // 注释 + 空行 + 表达式语义：注释不产生语句、不影响映射
            new Representative("comments-and-blank-lines", """
                    # leading comment
                    value = 1 + 2

                    # another comment
                    doubled = value * 2

                    globalThis.result = doubled
                    """, 6, 7));

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
        // 生产运行时由 @RegisterNekoJSPlugin 自动注册 PythonTranspilerPlugin；裸测试显式注册同一入口。
        compilers.registerLanguage("python", Set.of(".py"), new PythonToJsCompiler());
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
        Path file = paths.serverScripts().resolve("language-py-corpus/" + name + ".py");
        Files.createDirectories(file.getParent());
        Files.writeString(file, source);
        written.add(file);
        return file;
    }

    /**
     * 在同一个 Context 里执行一段模块产物。用 IIFE 包起来并剥离 {@code export} 块：
     * corpus 的多个条目会声明同名顶层绑定（如 {@code value}），连续 eval 会撞 "already declared"；
     * {@code export} 在脚本模式 eval 下也不是合法语法（导出形状由 ESM 用例单独覆盖）。
     */
    private Value execute(String code) {
        context.eval("js", "(function () {\n" + stripExports(code) + "\n})();\nglobalThis.result");
        return context.getBindings("js").getMember("result");
    }

    /** 去掉产物里的 ESM export 块，得到等价的可直接求值的模块体（纯测试辅助）。 */
    private static String stripExports(String code) {
        StringBuilder out = new StringBuilder();
        for (String line : code.split("\n", -1)) {
            if (line.trim().startsWith("export {")) {
                continue;
            }
            out.append(line).append('\n');
        }
        return out.toString();
    }

    // ---- AC1 / AC2 ----

    @Test
    void representativeCorpusProducesLanguageIdModeExecutableCodeAndUsableSourceMap() throws Exception {
        for (Representative representative : CORPUS) {
            Path file = write(representative.name(), representative.source());
            NekoPreparedModule prepared = preparation.prepare(file);

            assertEquals("python", prepared.languageId(),
                    representative.name() + " 的 language id 必须是语言注册表解析出的 python");
            assertEquals(NekoModuleMode.AUTO, prepared.requestedMode(),
                    representative.name() + " 的 .py 请求模式是 AUTO");
            assertEquals(NekoModuleMode.ESM, prepared.mode(),
                    representative.name() + " 的 .py 产物带 export 块 → 解析为 ESM");
            assertFalse(prepared.code().isBlank(), representative.name() + " 必须产出可执行 code");

            // 可执行 code：真的跑起来，产出预期值（不是只断言 contains）
            assertEquals(representative.expectedResult(), execute(prepared.code()).asInt(),
                    representative.name() + " 的产物必须可执行且行为正确：\n" + prepared.code());

            // 非空 source map（转译语言必须有真实映射）
            assertNotNull(prepared.sourceMap(), representative.name() + " 必须产出非空 source map");

            // 可用 source map：注册后能把产物行映射回原始 .py 的行
            String relative = paths.root().relativize(file).toString().replace('\\', '/');
            SourceMapRegistry.register(relative, prepared.sourceMap(), prepared.prependedLineCount());
            int generatedLine = lineOf(prepared.code(), "globalThis.result");
            SourceMapRegistry.OriginalPosition mapped =
                    SourceMapRegistry.getMappedPosition(relative, generatedLine, 1);
            assertNotNull(mapped.path, representative.name() + " 的 source map 必须能映射回原始源路径");
            assertEquals(relative, mapped.path, representative.name() + " 必须映射回同一原始文件");
            assertEquals(representative.expectedSourceLine(), mapped.line,
                    representative.name() + " 必须映射回原始 .py 中 globalThis.result 所在的行");
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

    /** .py 的 language id / requested mode / identity 必须在各层同源（不得出现第二种身份）。 */
    @Test
    void pythonLanguageIdentityIsConsistentAcrossLayers() throws Exception {
        Path file = write("identity", "def f():\n    return 1\nf()\n");
        NekoPreparedModule prepared = preparation.prepare(file);

        assertEquals("python", prepared.languageId());
        assertEquals(prepared.languageId(), prepared.cacheKey().languageId(),
                "cache key 的 language id 必须与可观察 language id 同源");
        assertEquals(prepared.languageId(), preparation.pipeline().identityOf(file).languageId(),
                "identityOf（缓存命中预判）必须与实际产物 language id 一致");
        assertEquals("python", compilers.getLanguage(".py").id(),
                "注册表对 .py 解析出的语言 id 必须与 prepared module 一致");
        assertEquals(file.normalize().toAbsolutePath(), prepared.sourcePath(),
                "prepared module 必须携带原始源路径（错误归属依赖它）");
    }

    // ---- AC4 / AC5：cache 命中与失效 ----

    @Test
    void unchangedPythonHitsCacheAndContentChangeInvalidatesIt() throws Exception {
        Path file = write("cache", "value = 1\n");
        NekoPreparedModule first = preparation.prepare(file);

        assertTrue(first == preparation.prepare(file), "未变化必须命中同一 prepared module 实例");

        Files.writeString(file, "value = 2\n");
        NekoPreparedModule afterEdit = preparation.prepare(file);
        assertNotSame(first, afterEdit, "内容变化必须失效缓存");
        assertFalse(afterEdit.cacheKey().equals(first.cacheKey()), "内容变化必须改变 cache key");
        assertTrue(afterEdit.code().contains("2"), "失效后必须用新内容重新转译：\n" + afterEdit.code());
    }

    /** 同路径同内容但该扩展名换了语言 → identity 变化必须失效（否则会返回旧语言产物）。 */
    @Test
    void languageIdentityChangeInvalidatesPythonCache() throws Exception {
        Path file = write("identity-change", "value = 1\n");
        NekoPreparedModule pythonOwned = preparation.prepare(file);

        ScriptCompilerRegistry replaced = ScriptCompilerRegistry.createRuntimeRegistry();
        replaced.registerLanguage("custompy", Set.of(".py"), new com.tkisor.nekojs.core.compiler.IScriptCompiler() {
            @Override public boolean canCompile(String extension) { return true; }
            @Override public String compile(Path path, String source) { return "globalThis.custom = 1;\n"; }
        });
        NekoModulePreparation other = TestModulePreparation.create(paths, replaced, SandboxConfig.defaultConfig());
        NekoPreparedModule custom = other.prepare(file);

        assertEquals("custompy", custom.languageId(), "换语言后 language id 必须变化");
        assertFalse(pythonOwned.cacheKey().equals(custom.cacheKey()),
                "language id 必须进入 cache key，否则会返回旧语言编译的模块");
        assertTrue(custom.code().contains("custom"), "失效后必须用新语言重新编译");
    }

    /** 准备失败必须留在准备阶段，不得留下缓存条目（失败不产生半成功状态）。 */
    @Test
    void failedPythonPreparationDoesNotPopulateCache() throws Exception {
        Path file = write("broken", "def f(:\n    pass\n");

        assertThrows(Exception.class, () -> preparation.prepare(file),
                "源语法错误必须在准备阶段失败");
        assertFalse(preparation.isCached(file), "准备失败不得留下 prepared 缓存条目");
    }
}
