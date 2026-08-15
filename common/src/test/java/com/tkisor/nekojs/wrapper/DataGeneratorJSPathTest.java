package com.tkisor.nekojs.wrapper;

import com.tkisor.nekojs.platform.Platform;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Path-boundary regression tests for {@link DataGeneratorJS#resolve(String)}.
 *
 * <p>All writes go through a temporary root inside the injected platform game dir, so
 * {@link com.tkisor.nekojs.core.fs.NekoJSPaths#get()} resolves to the test game dir.
 */
class DataGeneratorJSPathTest {
    private static Path gameDir;
    private Path root;

    @BeforeAll
    static void initPlatform() {
        TestPlatformInit.ensureInitialized();
        gameDir = Platform.getGameDir();
    }

    @BeforeEach
    void setUp() throws Exception {
        root = gameDir.resolve("nekojs").resolve("test-data-gen-" + System.nanoTime());
        Files.createDirectories(root);
    }

    @AfterEach
    void tearDown() throws Exception {
        deleteRecursively(root);
    }

    @Test
    void writesNestedPathUnderRoot() throws Exception {
        DataGeneratorJS generator = new DataGeneratorJS(root);

        generator.json("sub/foo.json", "{\"type\":\"minecraft:block\"}");

        Path file = root.resolve("sub").resolve("foo.json");
        assertTrue(Files.isRegularFile(file), "expected generated file at " + file);
        assertEquals("{\"type\":\"minecraft:block\"}", Files.readString(file));
    }

    @Test
    void rejectsTraversalAndAbsolutePaths() throws Exception {
        DataGeneratorJS generator = new DataGeneratorJS(root);

        assertThrows(IllegalArgumentException.class, () -> generator.json("../evil.json", "{}"));
        assertThrows(IllegalArgumentException.class, () -> generator.text("a/../../evil.txt", "x"));

        Path absoluteTarget = gameDir.toAbsolutePath().getRoot()
                .resolve("tmp")
                .resolve("nekojs-data-gen-evil-" + System.nanoTime() + ".json");
        try {
            assertThrows(IllegalArgumentException.class,
                    () -> generator.json(absoluteTarget.toString(), "{}"));
        } finally {
            Files.deleteIfExists(absoluteTarget);
        }
        assertTrue(Files.notExists(absoluteTarget));
    }

    @Test
    void rejectsSymlinkParentPointingOutsideGameDir() throws Exception {
        DataGeneratorJS generator = new DataGeneratorJS(root);

        Path outside = gameDir.getParent().resolve("nekojs-test-outside-" + System.nanoTime());
        Files.createDirectories(outside);
        Path link = root.resolve("link");
        try {
            Files.createSymbolicLink(link, outside);
        } catch (UnsupportedOperationException | SecurityException | IOException e) {
            Files.deleteIfExists(link);
            Files.deleteIfExists(outside.resolve("evil.json"));
            Files.deleteIfExists(outside);
            Assumptions.assumeTrue(false, "Symlink creation is not permitted in this environment: " + e);
            return;
        }

        try {
            assertThrows(IllegalArgumentException.class,
                    () -> generator.json("link/evil.json", "{}"),
                    "write through a symlinked parent escaping the game dir must be rejected");
            assertTrue(Files.notExists(outside.resolve("evil.json")),
                    "escaped file must not be created outside the game dir");
        } finally {
            Files.deleteIfExists(link);
            Files.deleteIfExists(outside.resolve("evil.json"));
            Files.deleteIfExists(outside);
        }
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
