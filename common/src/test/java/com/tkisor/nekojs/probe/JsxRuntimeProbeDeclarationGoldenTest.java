package com.tkisor.nekojs.probe;

import com.tkisor.nekojs.core.compiler.NodeModuleTypeDocs;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Keeps the committed JSX runtime declaration golden consumed by the TypeScript probe gate
 * ({@code npm run test:probe-types}) byte-identical to what {@link NodeModuleTypeDocs#extractTS}
 * extracts from the engine module {@code nekojs/node/modules/jsx-runtime.ts}.
 *
 * <p>The golden at {@code common/src/test/probe-ts/generated/jsx-runtime.d.ts} is included by
 * {@code common/src/test/probe-ts/tsconfig.json}, so the probe gate must not depend on any
 * build-directory side effect: it stays green on a fresh checkout. This test is the drift guard —
 * when the engine declaration changes, regenerate the golden via
 * {@code ./gradlew :common:regenerateGoldens} (or {@code :common:test
 * -Dnekojs.golden.regenerate=true}), review the diff, and commit it together with the source
 * change. The actual extraction is written to {@code common/build/probe-ts-actual/} for
 * inspection and never mutates the source tree outside regenerate mode.
 */
class JsxRuntimeProbeDeclarationGoldenTest {

    private static final Path GOLDEN_PATH = Path.of(
            "src", "test", "probe-ts", "generated", "jsx-runtime.d.ts");
    private static final Path ACTUAL_PATH = Path.of(
            "build", "probe-ts-actual", "jsx-runtime.d.ts");

    @Test
    void extractedJsxRuntimeDeclarationMatchesGolden() throws IOException {
        String source = readResource("nekojs/node/modules/jsx-runtime.ts");
        String actual = NodeModuleTypeDocs.extractTS(source);

        assertFalse(actual.isBlank(), actual);
        assertTrue(actual.contains("declare module 'nekojs/jsx-runtime' {"), actual);
        assertTrue(actual.contains("export namespace JSX {"), actual);
        assertTrue(actual.contains("interface IntrinsicElements"), actual);

        Files.createDirectories(ACTUAL_PATH.getParent());
        Files.writeString(ACTUAL_PATH, actual, StandardCharsets.UTF_8);

        // Regenerate mode (-Dnekojs.golden.regenerate=true, set by the regenerateGoldens task):
        // overwrite the golden with the actual output, then skip the assertion for human review.
        if (ProbeGoldenSupport.regenerateEnabled()) {
            Files.createDirectories(GOLDEN_PATH.getParent());
            Files.writeString(GOLDEN_PATH, actual, StandardCharsets.UTF_8);
            Assumptions.assumeTrue(false, "goldens regenerated; review and commit");
        }

        assertTrue(Files.exists(GOLDEN_PATH),
                "Golden file missing: " + GOLDEN_PATH.toAbsolutePath()
                        + "\nRun the test once, review the actual output at " + ACTUAL_PATH
                        + ", then copy it to " + GOLDEN_PATH);

        // Normalize line endings like ProbeTypeScriptFixtureWriterTest (CRLF checkouts vs LF output).
        String golden = normalize(Files.readString(GOLDEN_PATH, StandardCharsets.UTF_8));
        assertEquals(golden, normalize(actual),
                "Extracted jsx-runtime declaration does not match golden. "
                        + "If intentional, update the golden by copying:\n"
                        + "  " + ACTUAL_PATH + "\n  -> " + GOLDEN_PATH);
    }

    private static String normalize(String value) {
        return value.replace("\r\n", "\n").stripTrailing();
    }

    private static String readResource(String path) throws IOException {
        try (InputStream in = JsxRuntimeProbeDeclarationGoldenTest.class.getClassLoader()
                .getResourceAsStream(path)) {
            assertNotNull(in, "resource not found: " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
