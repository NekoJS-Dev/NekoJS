package com.tkisor.nekojs.wrapper;

import com.tkisor.nekojs.platform.Platform;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Write-size quota regression tests for {@link LangGeneratorJS#writeTo(Path, String)}.
 *
 * <p>Collected entries stay unbounded in memory, but the merged JSON text written to disk
 * must be capped so a script cannot fill the disk through {@code event.add}.
 */
class LangGeneratorQuotaTest {
    private static final int MAX_FILE = 16 * 1024 * 1024;

    private Path assetsRoot;

    @BeforeAll
    static void initPlatform() {
        TestPlatformInit.ensureInitialized();
    }

    @BeforeEach
    void setUp() throws Exception {
        assetsRoot = Platform.getGameDir().resolve("nekojs").resolve("test-lang-quota-" + System.nanoTime());
        Files.createDirectories(assetsRoot);
    }

    @AfterEach
    void tearDown() throws Exception {
        deleteRecursively(assetsRoot);
    }

    @Test
    void rejectsMergedLangJsonLargerThanPerFileQuota() throws Exception {
        LangGeneratorJS generator = new LangGeneratorJS("en_us");
        generator.add("quota.big", "x".repeat(MAX_FILE + 1));
        Path file = assetsRoot.resolve("lang").resolve("en_us.json");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> generator.writeTo(assetsRoot, "en_us"));
        assertTrue(error.getMessage().contains(String.valueOf(MAX_FILE)),
                "message should name the limit: " + error.getMessage());
        assertTrue(Files.notExists(file), "rejected write must not create the lang file");
        assertTrue(Files.notExists(file.getParent()), "rejected write must not create the lang directory");
    }

    @Test
    void smallLangFileWithinQuotaStillSucceeds() throws Exception {
        LangGeneratorJS generator = new LangGeneratorJS("en_us");
        generator.add("quota.small", "Small");

        generator.writeTo(assetsRoot, "en_us");

        Path file = assetsRoot.resolve("lang").resolve("en_us.json");
        assertTrue(Files.isRegularFile(file), "expected lang file at " + file);
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
