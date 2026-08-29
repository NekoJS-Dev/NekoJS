package com.tkisor.nekojs.network;

import com.tkisor.nekojs.core.fs.NekoJSPaths;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.RandomAccessFile;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * S3 quality follow-up: collectAllValidScripts must enforce the batch limits while
 * walking the tree, before reading file contents into memory. The tests install an
 * isolated NekoJSPaths instance over a @TempDir game dir so the global singleton is
 * never polluted for other tests.
 */
class ScriptSyncFilesCollectLimitTest {

    @TempDir
    Path tempDir;

    private Object previousPathsInstance;

    @BeforeEach
    void installIsolatedPaths() throws Exception {
        Field field = NekoJSPaths.class.getDeclaredField("INSTANCE");
        field.setAccessible(true);
        previousPathsInstance = field.get(null);
        field.set(null, NekoJSPaths.fromGameDir(tempDir));
    }

    @AfterEach
    void restorePaths() throws Exception {
        Field field = NekoJSPaths.class.getDeclaredField("INSTANCE");
        field.setAccessible(true);
        field.set(null, previousPathsInstance);
    }

    @Test
    void oversizedScriptIsRejectedWhileWalkingWithoutReadingIt() throws Exception {
        Path serverScripts = NekoJSPaths.get().serverScripts();
        Files.createDirectories(serverScripts);
        Path oversized = serverScripts.resolve("oversized.js");
        try (RandomAccessFile raf = new RandomAccessFile(oversized.toFile(), "rw")) {
            raf.setLength(ScriptSyncService.MAX_BATCH_SCRIPT_SIZE + 1L);
        }

        IllegalStateException ex = assertTimeoutPreemptively(Duration.ofSeconds(5),
                () -> assertThrows(IllegalStateException.class,
                        () -> ScriptSyncFiles.collectAllValidScripts(NekoJSPaths.get().root())));

        assertTrue(ex.getMessage().contains("脚本文件过大"), ex.getMessage());
        assertTrue(ex.getMessage().contains("oversized.js"), ex.getMessage());
    }

    @Test
    void tooManyScriptsAreRejectedWhileWalking() throws Exception {
        Path serverScripts = NekoJSPaths.get().serverScripts();
        Files.createDirectories(serverScripts);
        for (int i = 0; i <= ScriptSyncService.MAX_SYNC_FILES; i++) {
            Files.writeString(serverScripts.resolve("f" + i + ".js"), "// empty");
        }

        IllegalStateException ex = assertTimeoutPreemptively(Duration.ofSeconds(10),
                () -> assertThrows(IllegalStateException.class,
                        () -> ScriptSyncFiles.collectAllValidScripts(NekoJSPaths.get().root())));

        assertTrue(ex.getMessage().contains("脚本数量超过限制"), ex.getMessage());
    }

    @Test
    void oversizedTotalIsRejectedWhileWalking() throws Exception {
        Path serverScripts = NekoJSPaths.get().serverScripts();
        Files.createDirectories(serverScripts);
        long oneFile = ScriptSyncService.MAX_BATCH_SCRIPT_SIZE - 1; // 7 bytes short of 8MB
        for (int i = 0; i < 5; i++) {
            Path file = serverScripts.resolve("bulk" + i + ".js");
            try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "rw")) {
                raf.setLength(oneFile);
            }
        }

        IllegalStateException ex = assertTimeoutPreemptively(Duration.ofSeconds(10),
                () -> assertThrows(IllegalStateException.class,
                        () -> ScriptSyncFiles.collectAllValidScripts(NekoJSPaths.get().root())));

        assertTrue(ex.getMessage().contains("脚本总大小超过限制"), ex.getMessage());
    }
}
