package com.tkisor.nekojs.probe;

import com.tkisor.nekojs.api.catalog.NekoScriptCatalogSnapshot;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Generates the full probe tree from {@link LegacyProbeFixture#snapshot()} using
 * {@link ProbeOrchestrator} with {@link ProbeExternalArtifacts#NONE}, then compares
 * every file against golden resources under {@code /nekojs/probe/legacy-tree/}.
 */
class LegacyProbeTreeTest {

    @BeforeAll
    static void initPlatform() {
        TestPlatformInit.ensureInitialized();
    }

    @TempDir
    Path tempDir;

    @Test
    void legacyTreeMatchesGolden() throws Exception {
        NekoScriptCatalogSnapshot snapshot = LegacyProbeFixture.snapshot();
        ProbeOrchestrator orchestrator = new ProbeOrchestrator(ProbeExternalArtifacts.NONE);
        var result = orchestrator.generate(snapshot, tempDir.resolve("probe-types"));
        assertTrue(result.success(), "Probe generation should succeed: " + result.message());

        Map<String, String> actualFiles = readTree(tempDir.resolve("probe-types"));
        Map<String, String> expectedFiles = readGoldenTree();

        // Verify golden tree exists
        assertFalse(expectedFiles.isEmpty(), "Golden tree resources must exist under /nekojs/probe/legacy-tree/");

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

    private Map<String, String> readGoldenTree() throws IOException, URISyntaxException {
        Map<String, String> files = new TreeMap<>();
        String basePath = "/nekojs/probe/legacy-tree/";
        // Try to enumerate golden files from classpath resources
        var url = LegacyProbeTreeTest.class.getResource(basePath);
        if (url == null) return files;

        if ("file".equals(url.getProtocol())) {
            Path dir = Path.of(url.toURI());
            Files.walkFileTree(dir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    String rel = dir.relativize(file).toString().replace('\\', '/');
                    files.put(rel, normalize(Files.readString(file, StandardCharsets.UTF_8)));
                    return FileVisitResult.CONTINUE;
                }
            });
        }

        return files;
    }

    private static final Pattern IMPORT_PATTERN = Pattern.compile("import \\{([^}]+)\\} from \"([^\"]+)\";");

    private static String normalize(String value) {
        String normalized = value.replace("\r\n", "\n");
        // Sort imports within each import statement
        Matcher matcher = IMPORT_PATTERN.matcher(normalized);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String imports = matcher.group(1);
            String module = matcher.group(2);
            String[] parts = imports.split(",");
            String sorted = Arrays.stream(parts)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .sorted()
                    .collect(Collectors.joining(", "));
            matcher.appendReplacement(sb, Matcher.quoteReplacement("import { " + sorted + " } from \"" + module + "\";"));
        }
        matcher.appendTail(sb);
        // Sort all lines for deterministic comparison (handles non-deterministic member ordering)
        String[] lines = sb.toString().split("\n");
        Arrays.sort(lines);
        return String.join("\n", lines);
    }
}
