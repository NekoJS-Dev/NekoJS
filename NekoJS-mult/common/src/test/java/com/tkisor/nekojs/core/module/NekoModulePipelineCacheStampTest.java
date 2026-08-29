package com.tkisor.nekojs.core.module;

import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 白盒回归测试：{@link NekoModulePipelineCache.FileStamp} 必须包含内容指纹。
 *
 * <p>背景缺陷：FileStamp 只包含 (modifiedMillis, size)，在时间戳粒度较粗的文件系统上，
 * 同一毫秒内对等长文件的重写会被误判为未变化，导致 prepare 继续返回旧的已编译模块。
 * 修复后 FileStamp 增加 contentHash（从已读取的源码计算 SHA-256），等 mtime+size 但
 * 内容不同时必须失效；本测试还通过真实文件重写 + 显式恢复 mtime 验证行为层面确实重编译。
 */
class NekoModulePipelineCacheStampTest {

    @BeforeAll
    static void bindPaths() {
        TestPlatformInit.ensureInitialized();
    }

    @AfterEach
    void clearCaches() {
        NekoModulePipelineCache.clear();
    }

    // ---- FileStamp 内容指纹相等性 ----

    @Test
    void fileStampEqualsIncludesContentHash() throws Exception {
        Object stampA = newStamp(1L, 10L, "hash-a");
        Object sameContentDifferentMillis = newStamp(2L, 10L, "hash-a");
        Object sameContentDifferentSize = newStamp(1L, 20L, "hash-a");
        Object sameMillisAndSizeDifferentHash = newStamp(1L, 10L, "hash-b");
        Object identical = newStamp(1L, 10L, "hash-a");

        assertNotEquals(stampA, sameMillisAndSizeDifferentHash,
                "相同 mtime+size 但内容哈希不同必须视为不同 stamp");
        assertNotEquals(stampA, sameContentDifferentMillis,
                "内容哈希相同但 mtime 不同必须视为不同 stamp");
        assertNotEquals(stampA, sameContentDifferentSize,
                "内容哈希相同但 size 不同必须视为不同 stamp");
        assertEquals(stampA, identical, "mtime、size、contentHash 三元组相同时必须相等");
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
            NekoPreparedModule firstPrepared = NekoModulePipelineCache.prepare(script);
            assertTrue(firstPrepared.code().contains("AAAA1111"), "首次 prepare 应编译第一版内容");

            FileTime previousMtime = Files.getLastModifiedTime(script);
            Files.writeString(script, second);
            Files.setLastModifiedTime(script, previousMtime);

            NekoPreparedModule secondPrepared = NekoModulePipelineCache.prepare(script);
            assertTrue(secondPrepared.code().contains("BBBB2222"),
                    "等 mtime+size 但内容不同必须失效旧缓存并重新编译新内容");
        } finally {
            Files.deleteIfExists(script);
        }
    }

    // ---- 反射辅助：FileStamp 是 NekoModulePipelineCache 的私有嵌套 record ----

    private static Object newStamp(long millis, long size, String contentHash) throws Exception {
        Class<?> stampClass = Class.forName("com.tkisor.nekojs.core.module.NekoModulePipelineCache$FileStamp");
        Constructor<?> ctor = stampClass.getDeclaredConstructor(long.class, long.class, String.class);
        ctor.setAccessible(true);
        return ctor.newInstance(millis, size, contentHash);
    }
}
