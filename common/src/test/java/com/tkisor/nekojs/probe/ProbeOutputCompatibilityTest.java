package com.tkisor.nekojs.probe;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.catalog.NekoScriptCatalogSnapshot;
import com.tkisor.nekojs.api.surface.*;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that Probe output includes managed declarations and manifest alongside legacy files,
 * and that repeated generation produces identical output.
 */
class ProbeOutputCompatibilityTest {

    @BeforeAll
    static void initPlatform() {
        TestPlatformInit.ensureInitialized();
    }

    @TempDir
    Path tempDir;

    @Test
    void legacyFilesStillExistWithManagedOutput() throws Exception {
        NekoScriptCatalogSnapshot snapshot = LegacyProbeFixture.snapshot();
        ProbeOrchestrator orchestrator = new ProbeOrchestrator(ProbeExternalArtifacts.NONE);
        var result = orchestrator.generate(snapshot, tempDir.resolve("probe-types"));
        assertTrue(result.success(), "Probe generation should succeed: " + result.message());

        Map<String, String> actualFiles = readTree(tempDir.resolve("probe-types"));

        // All legacy golden files must still exist
        Map<String, String> legacyGolden = readGoldenTree();
        assertFalse(legacyGolden.isEmpty(), "Legacy golden tree must exist");

        for (String relPath : legacyGolden.keySet()) {
            assertTrue(actualFiles.containsKey(relPath),
                    "Legacy file missing from generated output: " + relPath);
        }
    }

    @Test
    void managedDeclarationsAreGenerated() throws Exception {
        NekoScriptCatalogSnapshot snapshot = LegacyProbeFixture.snapshot();
        ProbeOrchestrator orchestrator = new ProbeOrchestrator(ProbeExternalArtifacts.NONE);
        var result = orchestrator.generate(snapshot, tempDir.resolve("probe-types"));
        assertTrue(result.success(), result.message());

        Map<String, String> actualFiles = readTree(tempDir.resolve("probe-types"));

        // current-surface.json should exist
        assertTrue(actualFiles.containsKey("current-surface.json"),
                "current-surface.json should exist. Files: " + actualFiles.keySet());
    }

    @Test
    void repeatedGenerationProducesIdenticalFileHashes() throws Exception {
        NekoScriptCatalogSnapshot snapshot = LegacyProbeFixture.snapshot();
        ProbeOrchestrator orchestrator = new ProbeOrchestrator(ProbeExternalArtifacts.NONE);

        Path probeDir1 = tempDir.resolve("run1").resolve("probe-types");
        Path probeDir2 = tempDir.resolve("run2").resolve("probe-types");

        var result1 = orchestrator.generate(snapshot, probeDir1);
        assertTrue(result1.success(), result1.message());
        var result2 = orchestrator.generate(snapshot, probeDir2);
        assertTrue(result2.success(), result2.message());

        Map<String, String> files1 = readTree(probeDir1);
        Map<String, String> files2 = readTree(probeDir2);

        // Same set of files
        assertEquals(files1.keySet(), files2.keySet(),
                "File sets should be identical across runs");

        // Same content for each file
        for (String relPath : files1.keySet()) {
            assertEquals(files1.get(relPath), files2.get(relPath),
                    "Content mismatch for " + relPath + " across runs");
        }
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
        String basePath = "/nekojs/probe/legacy-tree/";
        var url = ProbeOutputCompatibilityTest.class.getResource(basePath);
        if (url == null) return files;

        if ("file".equals(url.getProtocol())) {
            try {
                Path dir = Path.of(url.toURI());
                Files.walkFileTree(dir, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        String rel = dir.relativize(file).toString().replace('\\', '/');
                        files.put(rel, normalize(Files.readString(file, StandardCharsets.UTF_8)));
                        return FileVisitResult.CONTINUE;
                    }
                });
            } catch (java.net.URISyntaxException e) {
                throw new IOException(e);
            }
        }
        return files;
    }

    private static String normalize(String value) {
        return value.replace("\r\n", "\n").stripTrailing();
    }
}
