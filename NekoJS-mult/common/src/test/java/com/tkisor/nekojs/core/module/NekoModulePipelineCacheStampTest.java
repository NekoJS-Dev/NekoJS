package com.tkisor.nekojs.core.module;

import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.testfixture.TestModulePreparation;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 回归测试：prepared module cache 的 stamp 必须包含<strong>内容指纹</strong>。
 *
 * <p>背景缺陷：stamp 只包含 (modifiedMillis, size)，在时间戳粒度较粗的文件系统上，
 * 同一毫秒内对等长文件的重写会被误判为未变化，导致 prepare 继续返回旧的已编译模块。
 * 修复后 stamp 增加 contentHash（从已读取的源码计算 SHA-256，见 {@link NekoContentHash}），
 * 等 mtime+size 但内容不同时必须失效；本测试还通过真实文件重写 + 显式恢复 mtime 验证
 * 行为层面确实重编译。
 *
 * <p>工单 11：cache 不再是 process-wide static {@code NekoModulePipelineCache}，
 * 而是 runtime owner 持有的实例级 {@link NekoModulePreparation}。测试改用
 * {@link TestModulePreparation} 构造独立实例，不再反射私有 static 字段。
 */
class NekoModulePipelineCacheStampTest {

    @BeforeAll
    static void bindPaths() {
        TestPlatformInit.ensureInitialized();
    }

    // ---- 内容指纹本身 ----

    @Test
    void contentHashDistinguishesEqualLengthContent() {
        assertNotEquals(NekoContentHash.sha256("module.exports = 'AAAA1111';"),
                NekoContentHash.sha256("module.exports = 'BBBB2222';"),
                "等长但内容不同必须产生不同指纹");
    }

    @Test
    void cacheKeyIncludesContentHash() {
        Path source = Path.of("nekojs/server_scripts/stamp-key.cjs");
        NekoModuleCacheKey first = NekoModuleCacheKey.of("javascript", com.tkisor.nekojs.core.compiler.NekoModuleMode.COMMONJS, source, "AAAA");
        NekoModuleCacheKey second = NekoModuleCacheKey.of("javascript", com.tkisor.nekojs.core.compiler.NekoModuleMode.COMMONJS, source, "BBBB");
        assertNotEquals(first, second, "路径与语言相同但内容不同必须产生不同 cache key");
        assertNotEquals(first.contentHash(), second.contentHash(), "cache key 必须携带内容指纹");
    }

    // ---- 行为回归：等长覆盖 + 显式恢复 mtime 仍必须重新编译 ----

    @Test
    void prepareRecompilesWhenOnlyContentChanges() throws Exception {
        NekoJSPaths paths = NekoJSPaths.get();
        NekoModulePreparation preparation = TestModulePreparation.create(paths, SandboxConfig.defaultConfig());
        Path script = paths.testScripts().resolve("stamp_invalidation_test.cjs");
        Files.createDirectories(script.getParent());

        // .cjs 走 CommonJS 原生分支，prepared.code() 即原始源码，便于断言新旧内容。
        String first = "module.exports = 'AAAA1111';\n";
        String second = "module.exports = 'BBBB2222';\n";
        assertTrue(first.length() == second.length(), "测试前提：两次写入必须等长");

        Files.writeString(script, first);
        try {
            NekoPreparedModule firstPrepared = preparation.prepare(script);
            assertTrue(firstPrepared.code().contains("AAAA1111"), "首次 prepare 应编译第一版内容");

            FileTime previousMtime = Files.getLastModifiedTime(script);
            Files.writeString(script, second);
            Files.setLastModifiedTime(script, previousMtime);

            NekoPreparedModule secondPrepared = preparation.prepare(script);
            assertTrue(secondPrepared.code().contains("BBBB2222"),
                    "等 mtime+size 但内容不同必须失效旧缓存并重新编译新内容");
        } finally {
            Files.deleteIfExists(script);
        }
    }

    /** 未变化的文件必须命中同一实例缓存（不重编译）。 */
    @Test
    void unchangedFileHitsCache() throws Exception {
        NekoJSPaths paths = NekoJSPaths.get();
        NekoModulePreparation preparation = TestModulePreparation.create(paths, SandboxConfig.defaultConfig());
        Path script = paths.testScripts().resolve("stamp_hit_test.cjs");
        Files.createDirectories(script.getParent());
        Files.writeString(script, "module.exports = 'hit';\n");
        try {
            NekoPreparedModule first = preparation.prepare(script);
            NekoPreparedModule second = preparation.prepare(script);
            assertTrue(first == second, "同一实例、未变化的文件必须返回同一 prepared module 实例（cache 命中）");
            assertTrue(preparation.isCached(script), "prepared 条目必须留在本实例缓存中");
        } finally {
            Files.deleteIfExists(script);
        }
    }

    /** 独立实例之间不共享缓存（工单 11：不再有 process-wide static cache）。 */
    @Test
    void independentPreparationsDoNotShareCache() throws Exception {
        NekoJSPaths paths = NekoJSPaths.get();
        SandboxConfig config = SandboxConfig.defaultConfig();
        NekoModulePreparation first = TestModulePreparation.create(paths, config);
        NekoModulePreparation second = TestModulePreparation.create(paths, config);
        Path script = paths.testScripts().resolve("stamp_isolation_test.cjs");
        Files.createDirectories(script.getParent());
        Files.writeString(script, "module.exports = 'isolated';\n");
        try {
            first.prepare(script);
            assertTrue(first.isCached(script), "第一个实例必须缓存自己准备的模块");
            assertTrue(!second.isCached(script), "第二个实例不得看到第一个实例的缓存（无 process-wide 共享）");
        } finally {
            Files.deleteIfExists(script);
        }
    }
}
