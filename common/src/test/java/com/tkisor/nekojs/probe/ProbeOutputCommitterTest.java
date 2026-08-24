package com.tkisor.nekojs.probe;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link ProbeOutputCommitter} 就地同步语义：写变化的、跳过相同的、删陈旧的、清历史遗留目录。
 *
 * <p>之前是「staging → 输出目录」的目录级 rename，在 Windows 上只要编辑器盯着输出目录就
 * 恒定 AccessDenied，故改成逐文件就地同步；这些测试固定新语义。
 */
class ProbeOutputCommitterTest {

    @Test
    void writesNewFilesAndCreatesNestedDirs(@TempDir Path tmp) throws Exception {
        Path out = tmp.resolve("typescript");

        var report = ProbeOutputCommitter.commitInPlace(out, Map.of(
                "index.d.ts", "root",
                "@package/net/minecraft/index.d.ts", "nested"));

        assertEquals("root", Files.readString(out.resolve("index.d.ts")));
        assertEquals("nested", Files.readString(out.resolve("@package/net/minecraft/index.d.ts")));
        assertEquals(2, report.written());
        assertEquals(0, report.unchanged());
        assertTrue(report.warnings().isEmpty());
    }

    @Test
    void unchangedFilesAreNotTouched(@TempDir Path tmp) throws Exception {
        Path out = tmp.resolve("typescript");
        ProbeOutputCommitter.commitInPlace(out, Map.of("a.d.ts", "same", "b.d.ts", "before"));
        Path untouched = out.resolve("a.d.ts");
        FileTime stamp = FileTime.fromMillis(1_000_000_000L);
        Files.setLastModifiedTime(untouched, stamp);

        var report = ProbeOutputCommitter.commitInPlace(out, Map.of("a.d.ts", "same", "b.d.ts", "after"));

        // 内容相同就不重写：少一次编辑器重载，也少一次和语言服务抢锁
        assertEquals(stamp, Files.getLastModifiedTime(untouched));
        assertEquals("after", Files.readString(out.resolve("b.d.ts")));
        assertEquals(1, report.written());
        assertEquals(1, report.unchanged());
    }

    @Test
    void staleFilesAndEmptyDirsAreRemoved(@TempDir Path tmp) throws Exception {
        Path out = tmp.resolve("typescript");
        ProbeOutputCommitter.commitInPlace(out, Map.of(
                "keep.d.ts", "keep",
                "gone/old.d.ts", "gone"));

        var report = ProbeOutputCommitter.commitInPlace(out, Map.of("keep.d.ts", "keep"));

        assertTrue(Files.exists(out.resolve("keep.d.ts")));
        assertFalse(Files.exists(out.resolve("gone/old.d.ts")), "本次不再产出的文件必须删掉");
        assertFalse(Files.exists(out.resolve("gone")), "空目录一并清理");
        assertEquals(1, report.deleted());
    }

    @Test
    void leftoverStagingAndOldDirsFromTheDirectorySwapEraAreCleanedUp(@TempDir Path tmp) throws Exception {
        Path out = tmp.resolve("typescript");
        Files.createDirectories(tmp.resolve("typescript.staging/@package"));
        Files.writeString(tmp.resolve("typescript.staging/@package/x.d.ts"), "half");
        Files.createDirectories(tmp.resolve("typescript.old"));
        Files.writeString(tmp.resolve("typescript.old/y.d.ts"), "old");

        var report = ProbeOutputCommitter.commitInPlace(out, Map.of("a.d.ts", "a"));

        assertFalse(Files.exists(tmp.resolve("typescript.staging")));
        assertFalse(Files.exists(tmp.resolve("typescript.old")));
        assertTrue(report.warnings().isEmpty());
    }

    @Test
    void escapingRelativePathIsRejectedBeforeWriting(@TempDir Path tmp) throws Exception {
        Path out = tmp.resolve("typescript");

        // firstIllegalRelativePath 是第一道闸；commitInPlace 自己也再挡一次
        assertEquals("../evil.d.ts",
                ProbeOutputCommitter.firstIllegalRelativePath(java.util.List.of("ok.d.ts", "../evil.d.ts")));
        assertThrows(IOException.class,
                () -> ProbeOutputCommitter.commitInPlace(out, Map.of("../evil.d.ts", "boom")));
        assertFalse(Files.exists(tmp.resolve("evil.d.ts")));
    }

    @Test
    void illegalRelativePathsAreDetected() {
        assertNull(ProbeOutputCommitter.firstIllegalRelativePath(
                java.util.List.of("a.d.ts", "@package/net/index.d.ts")));
        assertEquals("", ProbeOutputCommitter.firstIllegalRelativePath(java.util.List.of("")));
        assertEquals("a/../../b", ProbeOutputCommitter.firstIllegalRelativePath(java.util.List.of("a/../../b")));
    }

    @Test
    void utf8ContentRoundTrips(@TempDir Path tmp) throws Exception {
        Path out = tmp.resolve("typescript");
        String content = "/** 中文注释 `#${RegistryTypes.ItemTag}` */\n";

        ProbeOutputCommitter.commitInPlace(out, Map.of("doc.d.ts", content));

        assertEquals(content, Files.readString(out.resolve("doc.d.ts"), StandardCharsets.UTF_8));
        // 第二次同步必须判定为未变化（说明比较用的是 UTF-8 字节而不是平台默认编码）
        assertEquals(1, ProbeOutputCommitter.commitInPlace(out, Map.of("doc.d.ts", content)).unchanged());
    }

    @Test
    void deleteRecursiveIsNoOpForAbsentAndRemovesTree(@TempDir Path tmp) throws Exception {
        assertDoesNotThrow(() -> ProbeOutputCommitter.deleteRecursive(tmp.resolve("absent")));

        Path tree = tmp.resolve("tree");
        Files.createDirectories(tree.resolve("a/b"));
        Files.writeString(tree.resolve("a/b/leaf.txt"), "x");
        Files.writeString(tree.resolve("root.txt"), "x");

        ProbeOutputCommitter.deleteRecursive(tree);
        assertFalse(Files.exists(tree));
    }
}
