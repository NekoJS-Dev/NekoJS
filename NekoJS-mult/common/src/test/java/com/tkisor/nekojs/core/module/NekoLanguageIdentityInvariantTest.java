package com.tkisor.nekojs.core.module;

import com.tkisor.nekojs.core.compiler.NekoCompilationPipeline;
import com.tkisor.nekojs.core.compiler.NekoJavaScriptLanguagePlugin;
import com.tkisor.nekojs.core.compiler.NekoJsxLanguagePlugin;
import com.tkisor.nekojs.core.compiler.NekoLanguagePlugin;
import com.tkisor.nekojs.core.compiler.NekoTypeScriptLanguagePlugin;
import com.tkisor.nekojs.core.compiler.ScriptCompilerRegistry;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.testfixture.TestModulePreparation;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 工单 12 AC1 深层不变量：<strong>同一份脚本文件在全部层必须只有一种 language id</strong>。
 *
 * <p>历史缺陷（缺陷 A）：{@code .tsx} 的 language id 在两层之间不一致——
 * {@link NekoPreparedModule#languageId()} 来自编译器注册表（插件 id {@code "jsx"}），
 * 而 {@code NekoIRProgram.languageId()} 来自 {@code NekoJsxLexer} 写死的 {@code "tsx"}。
 * 同一文件同时报两种语言身份，破坏 cache key 的可解释性与「language id 是 Interface 可观察
 * 部分」的规格承诺。
 *
 * <p><strong>裁决：{@code .tsx} 归 {@code "jsx"}</strong>（JSX 语言族/插件 id），理由见
 * {@link #tsxIsOwnedByTheJsxLanguageFamilyNotASeparateLanguage()}。
 *
 * <p>本测试覆盖 {@code .js / .mjs / .cjs / .jsx / .ts / .tsx} 全体，从最高调用者 Seam
 * （{@link NekoModulePreparation#prepare} 与 {@link NekoCompilationPipeline#compile}）观察。
 */
class NekoLanguageIdentityInvariantTest {

    /** 每种扩展名一份 representative 源；值语义简单，避免预检噪音。 */
    private static final Map<String, String> SOURCES = new LinkedHashMap<>();

    static {
        SOURCES.put(".js", "const value = 1;\nglobalThis.marker = value;\n");
        SOURCES.put(".mjs", "export const value = 1;\n");
        SOURCES.put(".cjs", "module.exports = { value: 1 };\n");
        SOURCES.put(".jsx", "const el = <div>hi</div>;\n");
        SOURCES.put(".ts", "const value: number = 1;\nexport { value };\n");
        SOURCES.put(".tsx", "const el: unknown = <div>hi</div>;\nexport { el };\n");
    }

    private NekoJSPaths paths;
    private ScriptCompilerRegistry compilers;
    private NekoModulePreparation preparation;
    private NekoCompilationPipeline pipeline;
    private final List<Path> written = new ArrayList<>();

    @BeforeAll
    static void initPlatform() {
        TestPlatformInit.ensureInitialized();
    }

    @BeforeEach
    void setUp() {
        paths = NekoJSPaths.get();
        compilers = ScriptCompilerRegistry.createRuntimeRegistry();
        // 生产运行时由 NekoCommonBuiltinPlugin 注册 TS/JSX；.js/.mjs/.cjs 由管线兜底到
        // NekoJavaScriptLanguagePlugin。这里三者都显式注册，比对的是注册表本身而非兜底分支。
        compilers.register(NekoJavaScriptLanguagePlugin.INSTANCE);
        compilers.register(NekoTypeScriptLanguagePlugin.INSTANCE);
        compilers.register(NekoJsxLanguagePlugin.INSTANCE);
        preparation = TestModulePreparation.create(paths, compilers, SandboxConfig.defaultConfig());
        pipeline = new NekoCompilationPipeline();
    }

    @AfterEach
    void tearDown() throws IOException {
        // 真删文件 + 空的父目录：deleteOnExit 只在 JVM 退出时生效，会污染共享 gameDir
        TestModulePreparation.deleteFilesAndEmptyParents(written);
        written.clear();
    }

    private Path write(String extension) throws IOException {
        Path file = paths.serverScripts().resolve("language-identity/identity" + extension);
        Files.createDirectories(file.getParent());
        Files.writeString(file, SOURCES.get(extension));
        written.add(file);
        return file;
    }

    /**
     * AC1 的核心不变量：对每一种受支持扩展名，
     * <ol>
     *   <li>prepared module 的 language id == 编译器注册表对同一扩展名解析出的语言 id（缓存命中判定
     *       与实际产物同源）；</li>
     *   <li>prepared module 的 language id == 编译管线产出的 IR language id（同一文件只有一种身份）。</li>
     * </ol>
     * 任一层漂移本测试即变红。
     */
    @Test
    void preparedLanguageIdEqualsRegistryAndIrLanguageIdForEveryExtension() throws Exception {
        for (Map.Entry<String, String> entry : SOURCES.entrySet()) {
            String extension = entry.getKey();
            Path file = write(extension);
            String source = entry.getValue();

            NekoPreparedModule prepared = preparation.prepare(file);

            NekoLanguagePlugin plugin = compilers.getLanguagePlugin(extension);
            assertNotNull(plugin, extension + " 必须有注册的语言插件");
            assertEquals(plugin.id(), prepared.languageId(),
                    extension + " 的 prepared language id 必须等于注册表解析出的插件 id");

            String irLanguageId = pipeline.compile(file, source, extension, plugin, false).program().languageId();
            assertEquals(prepared.languageId(), irLanguageId,
                    extension + " 的 IR language id 必须与 prepared language id 一致"
                            + "（同一文件不得报出两种语言身份）");
        }
    }

    /**
     * 缺陷 A 的具体裁决：{@code .tsx} 归 {@code "jsx"}。
     *
     * <p>理由：{@code NekoJsxLanguagePlugin} 同时拥有 {@code .jsx} 与 {@code .tsx}，
     * {@code id()} 是 {@code "jsx"}。language id 的语义是「哪个语言 frontend 拥有这份文件」，
     * 而 TSX 只是同一 JSX frontend 的「JSX lowering + TS 擦除」组合形态——
     * 把它拆成第二个 id 会与 {@code ScriptCompilerRegistry.getLanguage}/{@code replaceLanguage}
     * 的 id 匹配、插件扩展名归属以及「identityOf 必须在编译前就能算出与产物一致的 id」
     * 三条既有契约同时冲突，却不能带来任何行为收益。
     *
     * <p>同时钉住「组合 frontend 仍然生效」：{@code .tsx} 的类型注解必须真的被擦除，
     * 证明归到 {@code jsx} 族没有把 TS 那一层弄丢。
     */
    @Test
    void tsxIsOwnedByTheJsxLanguageFamilyNotASeparateLanguage() throws Exception {
        Path tsx = write(".tsx");
        NekoPreparedModule prepared = preparation.prepare(tsx);

        assertEquals("jsx", prepared.languageId(),
                ".tsx 的 language id 是 JSX 语言族 id（注册表 plugin id），不是独立语言");
        assertEquals("jsx", compilers.getLanguage(".tsx").id(),
                "注册表对 .tsx 解析出的语言 id 必须与 prepared module 一致");
        assertFalse(prepared.code().contains(": unknown"),
                "归入 jsx 族不得丢掉 TS 擦除层：.tsx 的类型注解必须被擦除：\n" + prepared.code());
        assertTrue(prepared.code().contains("hi"),
                ".tsx 的 JSX 子树必须保留在产物里：\n" + prepared.code());

        // .jsx 不受影响：其 language id 仍是 jsx（不得因为对齐 .tsx 而被改坏）
        Path jsx = write(".jsx");
        assertEquals("jsx", preparation.prepare(jsx).languageId(),
                ".jsx 的 language id 必须保持 jsx");
    }

    /** language id 是 resolution/cache 的一部分：同一扩展名的 identity 与 cache key 必须同源。 */
    @Test
    void cacheKeyCarriesTheSameLanguageIdAsObservation() throws Exception {
        Set<String> seen = new java.util.LinkedHashSet<>();
        for (String extension : SOURCES.keySet()) {
            Path file = write(extension);
            NekoPreparedModule prepared = preparation.prepare(file);

            assertEquals(prepared.languageId(), prepared.cacheKey().languageId(),
                    extension + " 的 cache key language id 必须与可观察 language id 同源");
            assertEquals(prepared.languageId(), preparation.pipeline().identityOf(file).languageId(),
                    extension + " 的 identityOf（缓存命中预判）必须与实际产物 language id 一致");
            seen.add(prepared.languageId());
        }
        assertEquals(Set.of("javascript", "jsx", "typescript"), seen,
                "六种扩展名必须落到三种语言身份，且 JS/TS/JSX 一个都不少");
    }
}
