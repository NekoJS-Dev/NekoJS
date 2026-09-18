package com.tkisor.nekojs.core.module;

import com.tkisor.nekojs.core.compiler.IScriptCompiler;
import com.tkisor.nekojs.core.compiler.NekoModuleMode;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.compiler.ScriptCompilerRegistry;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 回归测试：{@link NekoModulePipelineCache} 的 {@code FileStamp} 必须包含内容指纹与
 * 语言/mode 身份（票据 11 AC4）。
 *
 * <p>背景缺陷：FileStamp 只包含 (modifiedMillis, size)，在时间戳粒度较粗的文件系统上，
 * 同一毫秒内对等长文件的重写会被误判为未变化，导致 prepare 继续返回旧的已编译模块。
 * 修复后 FileStamp 增加 contentHash（从已读取的源码计算 SHA-256），等 mtime+size 但
 * 内容不同时必须失效；W3 追加 languageId/requestedMode：同一路径在语言插件替换或
 * requested mode 变化时同样失效。本测试还通过真实文件重写 + 显式恢复 mtime 验证
 * 行为层面确实重编译。
 *
 * <p>W3 接缝说明：缓存是 runtime owner 持有的实例（构造器注入 pipeline），不再有
 * process-wide static；本测试经实例 seam 观察行为。
 */
class NekoModulePipelineCacheStampTest {

    @BeforeAll
    static void bindPaths() {
        TestPlatformInit.ensureInitialized();
    }

    private NekoModulePipelineCache cache;
    private ScriptCompilerRegistry registry;

    @org.junit.jupiter.api.BeforeEach
    void newCache() {
        registry = ScriptCompilerRegistry.createRuntimeRegistry();
        cache = NekoModulePipelineCache.withExplicitPipeline(registry, SandboxConfig.defaultConfig());
    }

    @AfterEach
    void clearCaches() {
        cache.clear();
    }

    // ---- FileStamp 内容指纹 + 语言/mode 相等性 ----

    @Test
    void fileStampEqualsIncludesContentHashLanguageAndMode() throws Exception {
        Object stampA = newStamp(1L, 10L, "hash-a", "javascript", NekoModuleMode.AUTO);
        Object sameMillisAndSizeDifferentHash =
                newStamp(1L, 10L, "hash-b", "javascript", NekoModuleMode.AUTO);
        Object sameContentDifferentMillis =
                newStamp(2L, 10L, "hash-a", "javascript", NekoModuleMode.AUTO);
        Object sameContentDifferentSize =
                newStamp(1L, 20L, "hash-a", "javascript", NekoModuleMode.AUTO);
        Object sameContentDifferentLanguage =
                newStamp(1L, 10L, "hash-a", "typescript", NekoModuleMode.AUTO);
        Object sameContentDifferentMode =
                newStamp(1L, 10L, "hash-a", "javascript", NekoModuleMode.ESM);
        Object identical = newStamp(1L, 10L, "hash-a", "javascript", NekoModuleMode.AUTO);

        assertNotEquals(stampA, sameMillisAndSizeDifferentHash,
                "相同 mtime+size 但内容哈希不同必须视为不同 stamp");
        assertNotEquals(stampA, sameContentDifferentMillis,
                "内容哈希相同但 mtime 不同必须视为不同 stamp");
        assertNotEquals(stampA, sameContentDifferentSize,
                "内容哈希相同但 size 不同必须视为不同 stamp");
        assertNotEquals(stampA, sameContentDifferentLanguage,
                "内容相同但 language identity 不同必须视为不同 stamp（语言插件替换即失效）");
        assertNotEquals(stampA, sameContentDifferentMode,
                "内容相同但 requested mode 不同必须视为不同 stamp");
        assertEquals(stampA, identical, "五元组相同时必须相等");
    }

    // ---- 行为回归：等长覆盖 + 显式恢复 mtime 仍必须重新编译 ----

    @Test
    void prepareRecompilesWhenOnlyContentChanges() throws Exception {
        Path script = NekoJSPaths.get().testScripts().resolve("stamp_invalidation_test.cjs");
        Files.createDirectories(script.getParent());

        // .cjs 走 CommonJS 原生分支，prepared.code() 即原始源码，便于断言新旧内容。
        String first = "module.exports = 'AAAA1111';\n";
        String second = "module.exports = 'BBBB2222';\n";
        assertEquals(first.length(), second.length(), "测试前提：两次写入必须等长");

        Files.writeString(script, first);
        try {
            NekoPreparedModule firstPrepared = cache.prepare(script);
            assertTrue(firstPrepared.code().contains("AAAA1111"), "首次 prepare 应编译第一版内容");

            FileTime previousMtime = Files.getLastModifiedTime(script);
            Files.writeString(script, second);
            Files.setLastModifiedTime(script, previousMtime);

            NekoPreparedModule secondPrepared = cache.prepare(script);
            assertTrue(secondPrepared.code().contains("BBBB2222"),
                    "等 mtime+size 但内容不同必须失效旧缓存并重新编译新内容");
            assertNotEquals(firstPrepared.cacheKey(), secondPrepared.cacheKey(),
                    "内容变化必须产生不同 cache key");
        } finally {
            Files.deleteIfExists(script);
        }
    }

    // ---- AC4：路径 / mode / language identity 变化即失效 ----

    @Test
    void sameContentAtDifferentPathsCachesIndependently() throws Exception {
        Path dir = NekoJSPaths.get().testScripts();
        Files.createDirectories(dir);
        Path first = dir.resolve("stamp_path_a.cjs");
        Path second = dir.resolve("stamp_path_b.cjs");
        Files.writeString(first, "module.exports = 'PATH';\n");
        Files.writeString(second, "module.exports = 'PATH';\n");
        try {
            NekoPreparedModule preparedA = cache.prepare(first);
            NekoPreparedModule preparedB = cache.prepare(second);
            assertEquals(preparedA.code(), preparedB.code(), "同内容产出同 code");
            // 注：恒等 source map 内嵌来源路径，同内容在不同路径下的 key 可以不同；
            // 路径身份由 map 键承担，key 只保证“同输入同 key”（稳定性见 PrepareTest）。

            cache.invalidate(first);
            // 失效其一不影响另一：second 仍命中旧条目（code 一致即命中证据）。
            NekoPreparedModule preparedBAgain = cache.prepare(second);
            assertEquals(preparedB.cacheKey(), preparedBAgain.cacheKey());
            assertTrue(preparedBAgain.code().contains("PATH"));
        } finally {
            Files.deleteIfExists(first);
            Files.deleteIfExists(second);
        }
    }

    @Test
    void sameContentWithDifferentModesCachesSeparately() throws Exception {
        Path dir = NekoJSPaths.get().testScripts();
        Files.createDirectories(dir);
        Path cjs = dir.resolve("stamp_mode.cjs");
        Path mjs = dir.resolve("stamp_mode.mjs");
        Files.writeString(cjs, "module.exports = 1;\n");
        Files.writeString(mjs, "export const value = 1;\n");
        try {
            NekoPreparedModule cjsPrepared = cache.prepare(cjs);
            NekoPreparedModule mjsPrepared = cache.prepare(mjs);

            assertEquals(NekoModuleMode.COMMONJS, cjsPrepared.mode());
            assertEquals(NekoModuleMode.ESM, mjsPrepared.mode());
            assertNotEquals(cjsPrepared.cacheKey(), mjsPrepared.cacheKey(),
                    "mode 变化必须产生不同 cache key");

            cache.clear();
            NekoPreparedModule cjsAgain = cache.prepare(cjs);
            assertEquals(cjsPrepared.cacheKey(), cjsAgain.cacheKey(), "全清后重建 key 稳定");
        } finally {
            Files.deleteIfExists(cjs);
            Files.deleteIfExists(mjs);
        }
    }

    @Test
    void languagePluginReplacementInvalidatesSamePath() throws Exception {
        Path dir = NekoJSPaths.get().testScripts();
        Files.createDirectories(dir);
        Path script = dir.resolve("stamp_language.js");
        String source = "module.exports = 'LANG';\n";
        Files.writeString(script, source);
        try {
            NekoPreparedModule before = cache.prepare(script);
            assertEquals("javascript", before.languageId());

            // 同一 registry 替换同扩展名语言（模拟插件变更）：同一 pipeline/同一缓存实例
            // 必须因 language identity 变化而失效旧条目。
            IScriptCompiler legacy = new IScriptCompiler() {
                @Override
                public boolean canCompile(String extension) {
                    return ".js".equalsIgnoreCase(extension);
                }

                @Override
                public String compile(Path file, String sourceCode) {
                    return "// legacy-bridge\n" + sourceCode;
                }
            };
            registry.registerLanguage("legacy-test-js", Set.of(".js"), legacy);

            NekoPreparedModule after = cache.prepare(script);
            assertEquals("legacy-test-js", after.languageId());
            assertTrue(after.code().startsWith("// legacy-bridge"), "新语言产物必须生效， was: " + after.code());
            assertNotEquals(before.cacheKey(), after.cacheKey(),
                    "language identity 变化必须产生不同 cache key");
        } finally {
            Files.deleteIfExists(script);
        }
    }

    // ---- 反射辅助：FileStamp 是 NekoModulePipelineCache 的私有嵌套 record ----

    private static Object newStamp(long millis, long size, String contentHash,
                                   String languageId, NekoModuleMode mode) throws Exception {
        Class<?> stampClass = Class.forName("com.tkisor.nekojs.core.module.NekoModulePipelineCache$FileStamp");
        Constructor<?> ctor = stampClass.getDeclaredConstructor(
                long.class, long.class, String.class, String.class, NekoModuleMode.class);
        ctor.setAccessible(true);
        return ctor.newInstance(millis, size, contentHash, languageId, mode);
    }
}
