package com.tkisor.nekojs.core.module;

import com.tkisor.nekojs.core.compiler.IScriptCompiler;
import com.tkisor.nekojs.core.compiler.NekoModuleMode;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.compiler.ScriptCompilerRegistry;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.error.SourceMapRegistry;
import com.tkisor.nekojs.core.module.NekoEsmVirtualModuleRegistry;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNotSame;
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
        cache = new NekoModulePipelineCache(
                new NekoModulePipeline(new com.tkisor.nekojs.core.compiler.NekoCompilationPipeline(),
                        registry, SandboxConfig.defaultConfig()),
                new SourceMapRegistry(NekoJSPaths.get().root()),
                new NekoEsmVirtualModuleRegistry(NekoJSPaths.get().root()), NekoTrustContext.local());
    }

    @AfterEach
    void clearCaches() {
        cache.clear();
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

    // ---- AC4: path / mode / language identity participate in observable identity ----

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
            assertNotEquals(preparedA.cacheKey(), preparedB.cacheKey(), "不同 sourcePath 必须有不同模块身份 key");

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
    void samePhysicalFileUsesCanonicalPathIdentity() throws Exception {
        Path script = NekoJSPaths.get().testScripts().resolve("stamp_canonical.cjs");
        Files.createDirectories(script.getParent());
        Files.writeString(script, "module.exports = 'CANONICAL';\n");
        try {
            NekoPreparedModule direct = cache.prepare(script);
            NekoPreparedModule normalized = cache.prepare(script.getParent().resolve(".").resolve(script.getFileName()));
            assertSame(direct, normalized, "等价规范路径应命中同一 prepared 条目");
        } finally {
            Files.deleteIfExists(script);
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

    @Test
    void compilerReplacementWithTheSameLanguageIdInvalidatesTheStamp() throws Exception {
        Path dir = NekoJSPaths.get().testScripts();
        Files.createDirectories(dir);
        Path script = dir.resolve("stamp_same_language_id.js");
        Files.writeString(script, "module.exports = 'SAME-ID';\n");
        try {
            IScriptCompiler firstCompiler = compilerWithPrefix("// first-compiler\n");
            registry.registerLanguage("same-id-js", Set.of(".js"), firstCompiler);
            NekoPreparedModule before = cache.prepare(script);
            assertTrue(before.code().startsWith("// first-compiler"));

            registry.replaceLanguage("same-id-js", Set.of(".js"), compilerWithPrefix("// second-compiler\n"));

            NekoPreparedModule after = cache.prepare(script);
            assertEquals("same-id-js", after.languageId());
            assertTrue(after.code().startsWith("// second-compiler"),
                    "a same-id compiler replacement must not reuse the old prepared module");
            assertNotEquals(before, after);
        } finally {
            Files.deleteIfExists(script);
        }
    }

    @Test
    void compilerCaptureKeepsPluginAndRevisionFromOneRegistryState() throws Exception {
        CountDownLatch lookupEntered = new CountDownLatch(1);
        CountDownLatch releaseLookup = new CountDownLatch(1);
        IScriptCompiler first = new IScriptCompiler() {
            @Override
            public boolean canCompile(String extension) {
                lookupEntered.countDown();
                try {
                    assertTrue(releaseLookup.await(5, TimeUnit.SECONDS));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(interrupted);
                }
                return ".race".equalsIgnoreCase(extension);
            }

            @Override
            public String compile(Path file, String sourceCode) {
                return sourceCode;
            }
        };
        registry.registerLanguage("same-race", Set.of(), first);

        var captureFuture = java.util.concurrent.CompletableFuture.supplyAsync(
                () -> registry.capture(".race"));
        assertTrue(lookupEntered.await(5, TimeUnit.SECONDS));
        Thread replacement = new Thread(() -> registry.replaceLanguage(
                "same-race", Set.of(".race"), compilerWithPrefix("// replacement\n")));
        replacement.start();
        releaseLookup.countDown();
        ScriptCompilerRegistry.Capture captured = captureFuture.get(5, TimeUnit.SECONDS);
        replacement.join(5000);

        assertSame(first, captured.compiler(), "capture must retain the compiler it looked up");
        assertNotSame(first, registry.capture(".race").compiler(),
                "the replacement becomes visible only after the atomic capture");
        assertNotEquals(captured.revision(), registry.revision(),
                "the captured revision must not be read after replacement");
    }

    private static IScriptCompiler compilerWithPrefix(String prefix) {
        return new IScriptCompiler() {
            @Override
            public boolean canCompile(String extension) {
                return ".js".equalsIgnoreCase(extension);
            }

            @Override
            public String compile(Path file, String sourceCode) {
                return prefix + sourceCode;
            }
        };
    }

}
