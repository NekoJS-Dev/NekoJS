package com.tkisor.nekojs.probe;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers the in-place sync behavior of {@link ProbeOutputCommitter}: write changed files, skip
 * identical ones, delete stale ones, and clean up leftover directories.
 *
 * <p>The tests pin down this per-file sync. A directory-level rename from a staging directory onto
 * the output directory failed on Windows with a constant AccessDenied whenever an editor was
 * watching the output directory.
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

        // Identical content is not rewritten: this saves an editor reload and avoids competing with the
        // language service for the file lock.
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

        // firstIllegalRelativePath is the first check; commitInPlace rejects an illegal path again itself.
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
        // The second sync must report the file unchanged, which shows the comparison uses UTF-8 bytes
        // rather than the platform default encoding.
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

    // ---- External occupation on Windows (an editor or the TS language service maps the .d.ts files) ----

    /**
     * On Windows a mapped file cannot be truncated or deleted (ERROR_USER_MAPPED_FILE becomes an
     * {@code AccessDeniedException}). That is exactly the lock the TS language service holds on the
     * probe output. The mapping behavior is Windows-specific, so other platforms skip this test.
     */
    private static boolean windows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private static MappedByteBuffer holdMapping(Path file) throws IOException {
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
            return channel.map(FileChannel.MapMode.READ_ONLY, 0, Files.size(file));
        }
    }

    /**
     * Releases the mapping deterministically. Without this, {@code @TempDir} cleanup fails on Windows
     * because the mapping outlives the test.
     */
    private static void releaseMapping(MappedByteBuffer buffer) {
        try {
            Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
            Field theUnsafe = unsafeClass.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            Object unsafe = theUnsafe.get(null);
            unsafeClass.getMethod("invokeCleaner", ByteBuffer.class).invoke(unsafe, buffer);
        } catch (ReflectiveOperationException | RuntimeException cleanupFailed) {
            // Fallback: drop the strong reference and let the Cleaner unmap the buffer. {@code @TempDir} cleanup
            // may then report the file as in use, but all assertions have already run.
            System.gc();
        }
    }

    @Test
    void lockedWriteFailsWithActionableMessageAfterRetries(@TempDir Path tmp) throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(windows(), "mapped-file locking is Windows semantics");

        Path out = tmp.resolve("typescript");
        Path locked = out.resolve("locked.d.ts");
        Files.createDirectories(out);
        Files.writeString(locked, "old");
        MappedByteBuffer mapping = holdMapping(locked);
        try {
            IOException failure = assertThrows(IOException.class, () ->
                    ProbeOutputCommitter.commitInPlace(out, Map.of("locked.d.ts", "new")));

            assertTrue(failure.getMessage().contains("locked by another process"),
                    "message must tell the user what holds the file: " + failure.getMessage());
            assertTrue(failure.getMessage().contains("locked.d.ts"),
                    "message must name the locked file: " + failure.getMessage());
            assertTrue(failure.getMessage().contains("/nekojs probe"),
                    "message must tell the user how to retry: " + failure.getMessage());
            assertNotNull(failure.getCause(), "底层错误（含『用户映射区域』字样）保留在 cause 里");
            assertEquals("old", Files.readString(locked), "被锁文件必须保持旧内容，不能半写");
        } finally {
            releaseMapping(mapping);
        }
    }
}
