package com.tkisor.nekojs.wrapper;

import com.tkisor.nekojs.platform.Platform;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Write-size quota regression tests for {@link DataGeneratorJS}.
 *
 * <p>A script must not be able to fill the disk through unbounded
 * {@code event.json}/{@code event.text} writes.
 */
class DataGeneratorQuotaTest {
    private static final int MAX_FILE = 16 * 1024 * 1024;
    private static final long MAX_TOTAL = 64 * 1024 * 1024L;

    private Path root;

    @BeforeAll
    static void initPlatform() {
        TestPlatformInit.ensureInitialized();
    }

    @BeforeEach
    void setUp() throws Exception {
        root = Platform.getGameDir().resolve("nekojs").resolve("test-data-quota-" + System.nanoTime());
        Files.createDirectories(root);
    }

    @AfterEach
    void tearDown() throws Exception {
        deleteRecursively(root);
    }

    @Test
    void rejectsSingleWriteLargerThanPerFileQuota() throws Exception {
        DataGeneratorJS generator = new DataGeneratorJS(root);
        String big = "x".repeat(MAX_FILE + 1);
        Path target = root.resolve("quota").resolve("too-big.txt");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> generator.text("quota/too-big.txt", big));
        assertTrue(error.getMessage().contains("quota/too-big.txt"),
                "message should name the rejected path: " + error.getMessage());
        assertTrue(error.getMessage().contains(String.valueOf(MAX_FILE)),
                "message should name the per-file limit: " + error.getMessage());
        assertTrue(Files.notExists(target), "rejected write must not create the target file");
        assertTrue(Files.notExists(target.getParent()), "rejected write must not create directories");
    }

    @Test
    void rejectsWriteThatWouldExceedCumulativeTotalQuota() throws Exception {
        DataGeneratorJS generator = new DataGeneratorJS(root);
        // 15 MiB per file: under the per-file cap, and 4 files (60 MiB) are under the
        // 64 MiB cumulative cap. A 5th 15 MiB file would reach 75 MiB and must be rejected.
        String chunk = "x".repeat(15 * 1024 * 1024);
        for (int i = 1; i <= 4; i++) {
            generator.text("quota/total-" + i + ".txt", chunk);
        }
        Path fifth = root.resolve("quota").resolve("total-5.txt");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> generator.text("quota/total-5.txt", chunk));
        assertTrue(error.getMessage().contains("quota/total-5.txt"),
                "message should name the rejected path: " + error.getMessage());
        assertTrue(Files.notExists(fifth), "rejected write must not create the target file");
        assertTrue(Files.isRegularFile(root.resolve("quota").resolve("total-4.txt")),
                "previous valid writes must still exist");
    }

    @Test
    void smallWriteWithinQuotasStillSucceeds() throws Exception {
        DataGeneratorJS generator = new DataGeneratorJS(root);

        generator.text("quota/small.txt", "hello");

        Path target = root.resolve("quota").resolve("small.txt");
        assertTrue(Files.isRegularFile(target), "expected generated file at " + target);
        assertEquals("hello", Files.readString(target, StandardCharsets.UTF_8));
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try (var stream = Files.walk(path)) {
            stream.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // best-effort cleanup
                }
            });
        }
    }
}
