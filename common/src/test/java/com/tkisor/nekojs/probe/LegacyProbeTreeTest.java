package com.tkisor.nekojs.probe;

import com.tkisor.nekojs.probe.backend.typescript.TypeScriptProbeBackend;
import com.tkisor.nekojs.api.catalog.NekoScriptCatalogSnapshot;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Generates the full probe tree from {@link LegacyProbeFixture#snapshot()} using
 * {@link TypeScriptProbeBackend}, then compares every file against the golden resources under
 * {@code /nekojs/probe/legacy-tree/}.
 *
 * <p><b>Why this test exists</b>: the content comparison overlaps
 * {@link ProbeOutputCompatibilityTest}, but this test also checks that no extra file appears -
 * only the {@code @nekojs/managed/} subtree may exist outside the golden - so it catches new
 * package directories or output files. Normalization uses the same byte-level rule as
 * {@link ProbeOutputCompatibilityTest} (only CRLF and trailing whitespace are normalized)
 * instead of sorting all lines and imports: sorting hides member-order regressions, and output
 * order is already deterministic (TypeReflector sorting plus lexicographic imports).
 */
class LegacyProbeTreeTest {

    private static final String GOLDEN_BASE_PATH = "/nekojs/probe/legacy-tree/";

    @BeforeAll
    static void initPlatform() {
        TestPlatformInit.ensureInitialized();
    }

    @TempDir
    Path tempDir;

    @Test
    void legacyTreeMatchesGolden() throws Exception {
        NekoScriptCatalogSnapshot snapshot = LegacyProbeFixture.snapshot();
        var result = generateAt(snapshot, tempDir.resolve("probe-types"));
        assertTrue(result.success(), "Probe generation should succeed: " + result.message());

        Map<String, String> actualFiles = readTree(tempDir.resolve("probe-types"));

        // Regenerate mode (-Dnekojs.golden.regenerate=true, set by the regenerateGoldens Gradle task): mirror
        // the actual output over the golden resource directory, then skip the assertions for a manual
        // review and commit
        if (ProbeGoldenSupport.regenerateEnabled()) {
            Path goldenDir = ProbeGoldenSupport.resourceDir(getClass(), GOLDEN_BASE_PATH);
            assertNotNull(goldenDir, "golden tree resources must resolve to a file: URL under " + GOLDEN_BASE_PATH);
            ProbeGoldenSupport.mirrorTree(tempDir.resolve("probe-types"), goldenDir);
            Assumptions.assumeTrue(false, "goldens regenerated; review and commit");
        }

        Map<String, String> expectedFiles = readGoldenTree();

        // Verify golden tree exists
        assertFalse(expectedFiles.isEmpty(), "Golden tree resources must exist under " + GOLDEN_BASE_PATH);

        // Check all expected files exist with matching content
        for (var entry : expectedFiles.entrySet()) {
            String relPath = entry.getKey();
            String expectedContent = entry.getValue();
            assertTrue(actualFiles.containsKey(relPath), "Missing generated file: " + relPath);
            assertEquals(expectedContent, actualFiles.get(relPath),
                    "Content mismatch for " + relPath);
        }

        // Check no extra files generated (allowing managed declarations subtree)
        for (String relPath : actualFiles.keySet()) {
            if (relPath.startsWith("@nekojs/managed/")) {
                continue;
            }
            assertTrue(expectedFiles.containsKey(relPath), "Unexpected generated file: " + relPath);
        }
    }

    /**
     * Generates into the given directory with TypeScriptProbeBackend. Passes the five-prefix
     * allowlist explicitly so the output stays byte-comparable with the golden tree.
     */
    private ProbeBackend.GenerateResult generateAt(NekoScriptCatalogSnapshot snapshot, Path outputDir) {
        ProbeConfig cfg = new ProbeConfig(true, ".neko_probe", new ProbeConfig.ScanConfig(
                List.of("java", "net.minecraft", "net.minecraftforge", "net.neoforged", "com.tkisor.nekojs"),
                List.of(), List.of(), List.of("minecraft"), 5, "SMART"));
        List<Class<?>> collected = new ArrayList<>(ProbeCoordinator.collectClasses(snapshot, cfg));
        TypeScriptProbeBackend backend = new TypeScriptProbeBackend();
        ProbeContext ctx = new ProbeContext.Of(snapshot, collected, cfg, NekoJSPaths.get(), "typescript", outputDir);
        return backend.generate(ctx);
    }

    private Map<String, String> readTree(Path root) throws IOException {
        Map<String, String> files = new TreeMap<>();
        if (!Files.exists(root)) return files;
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                String rel = root.relativize(file).toString().replace('\\', '/');
                files.put(rel, normalize(Files.readString(file, StandardCharsets.UTF_8)));
                return FileVisitResult.CONTINUE;
            }
        });
        return files;
    }

    private Map<String, String> readGoldenTree() throws IOException {
        Map<String, String> files = new TreeMap<>();
        Path dir = ProbeGoldenSupport.resourceDir(LegacyProbeTreeTest.class, GOLDEN_BASE_PATH);
        if (dir == null) return files;

        Files.walkFileTree(dir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                String rel = dir.relativize(file).toString().replace('\\', '/');
                files.put(rel, normalize(Files.readString(file, StandardCharsets.UTF_8)));
                return FileVisitResult.CONTINUE;
            }
        });
        return files;
    }

    /** Same rule as {@link ProbeOutputCompatibilityTest}: normalize only CRLF and trailing whitespace, never reorder. */
    private static String normalize(String value) {
        return value.replace("\r\n", "\n").stripTrailing();
    }
}
