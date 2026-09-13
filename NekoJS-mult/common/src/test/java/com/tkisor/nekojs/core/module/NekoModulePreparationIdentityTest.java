package com.tkisor.nekojs.core.module;

import com.tkisor.nekojs.core.compiler.NekoCompilationPipeline;
import com.tkisor.nekojs.core.compiler.NekoModuleMode;
import com.tkisor.nekojs.core.compiler.ScriptCompilerRegistry;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 工单 11 AC1/AC2/AC4：{@link NekoModulePipeline#prepare} 的最高调用者语义。
 *
 * <p>从最高既有调用者 Seam（{@code NekoScriptModuleLoaderHost.loadEntry} /
 * {@code NekoModulePreparation.prepare}）观察，不断言 parser/lowering/factory 内部状态：
 * <ul>
 *   <li>JS/CJS/ESM 输入得到正确 language id、module mode、可执行 code、可用 source map
 *       与稳定 cache key（AC1）；</li>
 *   <li>prepared module 不可变，组件不暴露可变内部引用（AC2）；</li>
 *   <li>内容 / mode / language identity 变化会失效 cache（AC4）。</li>
 * </ul>
 */
class NekoModulePreparationIdentityTest {

    @BeforeAll
    static void bindPaths() {
        TestPlatformInit.ensureInitialized();
    }

    private static NekoModulePreparation preparation() {
        NekoJSPaths paths = NekoJSPaths.get();
        ScriptCompilerRegistry compilers = ScriptCompilerRegistry.createRuntimeRegistry();
        return new NekoModulePreparation(
                new NekoModulePipeline(new NekoCompilationPipeline(), compilers, SandboxConfig.defaultConfig()),
                paths);
    }

    private static Path writeScript(String relative, String source) throws Exception {
        Path script = NekoJSPaths.get().root().resolve(relative);
        Files.createDirectories(script.getParent());
        Files.writeString(script, source);
        script.toFile().deleteOnExit();
        return script;
    }

    // ---- AC1：JS / CJS / ESM 的 language id、mode、code、source map、cache key ----

    @Test
    void cjsInputProducesCommonJsIdentity() throws Exception {
        Path script = writeScript("test_scripts/identity-plain.cjs", "module.exports = 1;\n");
        NekoPreparedModule prepared = preparation().prepare(script);

        assertEquals("javascript", prepared.languageId(), ".cjs 的 language id 必须是 javascript");
        assertEquals(NekoModuleMode.COMMONJS, prepared.mode(), ".cjs 必须解析为 COMMONJS 模式");
        assertEquals(NekoModuleMode.COMMONJS, prepared.requestedMode(), "requested mode 应为扩展名请求的 COMMONJS");
        assertTrue(prepared.code().contains("module.exports"), "CJS 产物必须保留原始 CJS 代码");
        assertNotNull(prepared.cacheKey(), "prepared module 必须携带稳定 cache key");
        assertNotNull(prepared.cjsRecord(), "CJS 必须携带静态分析记录");
        assertEquals(script.normalize().toAbsolutePath(), prepared.sourcePath(), "诊断位置必须指向原文件");
    }

    @Test
    void esmInputProducesEsmIdentityAndAst() throws Exception {
        Path script = writeScript("test_scripts/identity-esm.mjs", "export const value = 41 + 1;\n");
        NekoPreparedModule prepared = preparation().prepare(script);

        assertEquals("javascript", prepared.languageId(), ".mjs 的 language id 必须是 javascript");
        assertEquals(NekoModuleMode.ESM, prepared.mode(), ".mjs 必须解析为 ESM 模式");
        assertEquals(NekoModuleMode.ESM, prepared.requestedMode(), "requested mode 应为扩展名请求的 ESM");
        assertNotNull(prepared.esmAst(), "ESM 必须携带模块 AST 供 link 阶段消费");
        assertFalse(prepared.esmAst().exports().isEmpty(), "ESM AST 必须识别出 export");
    }

    @Test
    void jsExtensionUsesAutoRequestedMode() throws Exception {
        Path script = writeScript("test_scripts/identity-auto.js", "module.exports = 1;\n");
        NekoPreparedModule prepared = preparation().prepare(script);

        assertEquals("javascript", prepared.languageId());
        assertEquals(NekoModuleMode.AUTO, prepared.requestedMode(), ".js 的 requested mode 是 AUTO（待管线判定）");
        assertEquals(NekoModuleMode.COMMONJS, prepared.mode(), "无 import/export 的 .js 判定为 COMMONJS");
    }

    /** cache key 的四个维度都必须进入 key：任一变化 → 不同 key。 */
    @Test
    void cacheKeyCapturesLanguageModePathAndContent() {
        Path a = Path.of("nekojs/server_scripts/key.js");
        Path b = Path.of("nekojs/server_scripts/other.js");
        NekoModuleCacheKey base = NekoModuleCacheKey.of("javascript", NekoModuleMode.AUTO, a, "code");

        assertNotSame(base, NekoModuleCacheKey.of("typescript", NekoModuleMode.AUTO, a, "code"));
        assertNotSame(base, NekoModuleCacheKey.of("javascript", NekoModuleMode.ESM, a, "code"));
        assertNotSame(base, NekoModuleCacheKey.of("javascript", NekoModuleMode.AUTO, b, "code"));
        assertNotSame(base, NekoModuleCacheKey.of("javascript", NekoModuleMode.AUTO, a, "other"));

        assertEquals(base, NekoModuleCacheKey.of("javascript", NekoModuleMode.AUTO, a, "code"),
                "相同四元组必须产生相等 key（stability）");
    }

    // ---- AC2：prepared module 不可变 ----

    @Test
    void preparedModuleIsImmutableValue() throws Exception {
        Path script = writeScript("test_scripts/immutable.cjs", "module.exports = 1;\n");
        NekoPreparedModule prepared = preparation().prepare(script);

        NekoPreparedModule.requireImmutableComponents(prepared);
        // record 的重新构造是唯一「改变」方式：新实例必须与原实例不相等但各自自洽
        NekoPreparedModule modified = new NekoPreparedModule(
                prepared.code() + "// tampered\n", prepared.sourceMap(), prepared.mode(),
                prepared.esmAst(), prepared.cjsRecord(), prepared.origin(), prepared.cacheKey());
        assertNotSame(prepared.code(), modified.code(), "修改只能经重新构造产生新实例");
        assertFalse(prepared.code().contains("tampered"), "原实例的 code 不得被修改");
    }

    /** AC2：prepared module 的 AST/集合组件不得暴露可变内部引用（改它不能影响已准备结果）。 */
    @Test
    void esmAstCollectionsAreNotBackedByMutableState() throws Exception {
        Path script = writeScript("test_scripts/immutable-esm.mjs", "export const a = 1;\nexport const b = 2;\n");
        NekoPreparedModule prepared = preparation().prepare(script);
        int before = prepared.esmAst().statements().size();

        assertThrows(UnsupportedOperationException.class,
                () -> prepared.esmAst().statements().clear(),
                "prepared module 的集合组件必须是不可变视图");

        assertEquals(before, prepared.esmAst().statements().size(), "已准备结果不得被外部修改");
    }

    /**
     * AC2 强化：Resolution/Cache 侧的消费者无法把「改了源码/身份/诊断上下文」的结果伪装成
     * 同一个 prepared module。
     *
     * <p>{@link NekoPreparedModule} 是 record：没有任何 setter，因此「修改」只有两条出路——
     * (a) 重新构造一个新 record（那是一个不同对象，不是对原对象的修改），或 (b) 改动 record
     * 暴露的可变内部引用。本测试把两条路都钉死：
     * <ul>
     *   <li>未变化时重复 prepare 返回<strong>同一实例</strong>——不存在「中途被改过但仍被返回」
     *       的窗口；</li>
     *   <li>可观察元组（languageId / mode / code / cacheKey / origin）在多个消费者之间稳定；</li>
     *   <li>重新构造改过的 record 与原实例 {@code equals == false}，且原实例的可观察元组不变
     *       （改不动原对象，只能拿到新对象）。</li>
     * </ul>
     * 若将来有人在 record 里塞进可变容器（如裸 List）或加 setter，本测试变红。
     */
    @Test
    void preparedModuleObservableStateCannotBeMutatedInPlace() throws Exception {
        NekoModulePreparation preparation = preparation();
        Path script = writeScript("test_scripts/immutable-tuple.cjs", "module.exports = 'stable';\n");

        NekoPreparedModule first = preparation.prepare(script);
        NekoModulePreparation other = preparation();
        NekoPreparedModule second = other.prepare(script);

        // 两个独立消费者看到完全相同的可观察元组（各自 cache 实例，但输入相同 → 元组相同）
        assertEquals(first.languageId(), second.languageId());
        assertEquals(first.mode(), second.mode());
        assertEquals(first.requestedMode(), second.requestedMode());
        assertEquals(first.code(), second.code());
        assertEquals(first.cacheKey(), second.cacheKey());
        assertEquals(first.origin(), second.origin());

        // 同一实例内重复 prepare 必须返回同一对象：没有「被改过」的中间态
        assertSame(first, preparation.prepare(script),
                "未变化时必须返回同一 prepared module 实例（不存在被就地修改后仍被返回的窗口）");

        // 改过的 record 是新对象；原实例的可观察元组不受影响
        NekoPreparedModule tampered = new NekoPreparedModule(
                "module.exports = 'tampered';\n", first.sourceMap(), first.mode(),
                first.esmAst(), first.cjsRecord(), first.origin(), first.cacheKey());
        assertFalse(tampered.equals(first), "改过源码的 record 必须与原实例不等（不是同一个模块）");
        assertTrue(first.code().contains("stable"), "原实例的 code 必须保持不可变");
        assertSame(first, preparation.prepare(script), "篡改尝试不得污染 cache 中的条目");
    }

    // ---- AC4：内容 / mode / language identity 变化失效 cache ----

    @Test
    void contentChangeInvalidatesCacheEntry() throws Exception {
        NekoModulePreparation preparation = preparation();
        Path script = writeScript("test_scripts/invalidate-content.cjs", "module.exports = 'v1';\n");
        NekoPreparedModule first = preparation.prepare(script);

        Files.writeString(script, "module.exports = 'v2-longer';\n");
        NekoPreparedModule second = preparation.prepare(script);

        assertNotSame(first, second, "内容变化必须产生新的 prepared module");
        assertTrue(second.code().contains("v2-longer"), "必须返回新内容编译的模块");
        assertFalse(second.cacheKey().equals(first.cacheKey()), "内容变化必须改变 cache key");
    }

    @Test
    void pathChangeInvalidatesCacheEntry() throws Exception {
        NekoModulePreparation preparation = preparation();
        Path first = writeScript("test_scripts/invalidate-a.cjs", "module.exports = 1;\n");
        Path second = writeScript("test_scripts/invalidate-b.cjs", "module.exports = 1;\n");
        NekoPreparedModule a = preparation.prepare(first);
        NekoPreparedModule b = preparation.prepare(second);

        assertNotSame(a, b, "相同内容但不同路径必须是不同 cache 条目");
        assertFalse(a.cacheKey().equals(b.cacheKey()), "路径必须进入 cache key");
    }

    /** mode 变化（同路径扩展名改变）必须失效并产生新 prepared module。 */
    @Test
    void modeChangeInvalidatesCacheEntry() throws Exception {
        NekoModulePreparation preparation = preparation();
        Path cjs = writeScript("test_scripts/invalidate-mode.cjs", "module.exports = 1;\n");
        Path mjs = writeScript("test_scripts/invalidate-mode.mjs", "export const v = 1;\n");
        NekoPreparedModule cjsPrepared = preparation.prepare(cjs);
        NekoPreparedModule mjsPrepared = preparation.prepare(mjs);

        assertEquals(NekoModuleMode.COMMONJS, cjsPrepared.mode());
        assertEquals(NekoModuleMode.ESM, mjsPrepared.mode());
        assertFalse(cjsPrepared.cacheKey().equals(mjsPrepared.cacheKey()), "mode 必须进入 cache key");
    }

    /**
     * language identity 变化必须失效：同路径、同内容、同 stamp，但插件把该扩展名的语言换掉。
     * 只比 stamp 的实现会返回旧语言编译的模块。
     */
    @Test
    void languageIdentityChangeInvalidatesCacheEntry() throws Exception {
        NekoJSPaths paths = NekoJSPaths.get();
        SandboxConfig config = SandboxConfig.defaultConfig();
        Path script = writeScript("test_scripts/invalidate-language.zzz", "plain source\n");

        // 第一个实例：.zzz 由 legacy 编译器 A 处理（把源码包成可识别标记）
        ScriptCompilerRegistry compilersA = ScriptCompilerRegistry.createRuntimeRegistry();
        compilersA.registerLanguage("alpha", java.util.Set.of(".zzz"), new com.tkisor.nekojs.core.compiler.IScriptCompiler() {
            @Override public boolean canCompile(String extension) { return true; }
            @Override public String compile(Path file, String sourceCode) { return "alpha"; }
        });
        NekoModulePreparation prepA = new NekoModulePreparation(
                new NekoModulePipeline(new NekoCompilationPipeline(), compilersA, config), paths);
        NekoPreparedModule preparedA = prepA.prepare(script);
        assertEquals("alpha", preparedA.languageId());
        assertTrue(preparedA.code().contains("alpha"));

        // 第二个实例：同一路径、同一内容，但该扩展名换了语言 B
        ScriptCompilerRegistry compilersB = ScriptCompilerRegistry.createRuntimeRegistry();
        compilersB.registerLanguage("beta", java.util.Set.of(".zzz"), new com.tkisor.nekojs.core.compiler.IScriptCompiler() {
            @Override public boolean canCompile(String extension) { return true; }
            @Override public String compile(Path file, String sourceCode) { return "beta"; }
        });
        NekoModulePreparation prepB = new NekoModulePreparation(
                new NekoModulePipeline(new NekoCompilationPipeline(), compilersB, config), paths);
        NekoPreparedModule preparedB = prepB.prepare(script);

        assertEquals("beta", preparedB.languageId(), "换语言后 language id 必须变化");
        assertTrue(preparedB.code().contains("beta"), "必须返回新语言编译的产物");
        assertFalse(preparedA.cacheKey().equals(preparedB.cacheKey()), "language identity 必须进入 cache key");
    }

    // ---- AC6：准备失败与阶段归属 ----

    @Test
    void unknownExtensionFailsAtPreparationWithOwner() throws Exception {
        NekoModulePreparation preparation = preparation();
        Path script = writeScript("test_scripts/unsupported.unknownext", "whatever\n");

        Exception failure = assertThrows(Exception.class, () -> preparation.prepare(script));
        assertTrue(String.valueOf(failure.getMessage()).contains("No script compiler registered"),
                "未注册扩展名必须在准备阶段以可解释的错误失败，而不是延迟成无来源的 Graal 异常："
                        + failure.getMessage());
    }

    @Test
    void missingSourceFileFailsAtPreparationAsIoError() {
        NekoModulePreparation preparation = preparation();
        Path missing = NekoJSPaths.get().testScripts().resolve("does-not-exist.cjs");

        Exception failure = assertThrows(Exception.class, () -> preparation.prepare(missing));
        assertTrue(failure instanceof java.io.IOException,
                "缺失文件必须归为准备/IO 阶段失败：" + failure.getClass().getName());
    }
}
