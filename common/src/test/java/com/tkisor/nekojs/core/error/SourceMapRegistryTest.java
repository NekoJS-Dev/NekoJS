package com.tkisor.nekojs.core.error;

import com.tkisor.nekojs.core.error.SourceMapRegistry.OriginalPosition;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for {@link SourceMapRegistry}: exact-match resolution (the RISK-C3 removal
 * of the suffix fallback), rejection of 'sections' format and malformed JSON, and miss
 * passthrough (TEST-1c, locks DX-1 + PERF-3/4).
 */
class SourceMapRegistryTest {

    @BeforeAll
    static void bindPaths() {
        TestPlatformInit.ensureInitialized();
    }

    @BeforeEach
    void clearRegistry() {
        SourceMapRegistry.clear();
    }

    @Test
    void exactMatchResolvesOriginalPosition() {
        SourceMapRegistry.register("startup_scripts/foo.ts", minimalMap("startup_scripts/foo.ts"));
        OriginalPosition p = SourceMapRegistry.getMappedPosition("startup_scripts/foo.ts", 1, 0);
        assertNotNull(p.path, "exact key must resolve to a source path");
    }

    @Test
    void suffixOnlyQueryDoesNotResolve() {
        // RISK-C3: the old endsWith fallback was removed; only exact normalized keys match.
        SourceMapRegistry.register("startup_scripts/foo.ts", minimalMap("startup_scripts/foo.ts"));
        OriginalPosition p = SourceMapRegistry.getMappedPosition("other/foo.ts", 1, 0);
        assertNull(p.path, "a non-exact suffix query must not resolve");
        assertEquals(1, p.line, "miss should pass through the requested line");
    }

    @Test
    void sectionsFormatIsRejectedAsPassthrough() {
        String sections = "{\"version\":3,\"sections\":[{\"offset\":{\"line\":0,\"column\":0},"
                + "\"map\":{\"version\":3,\"sources\":[\"x\"],\"names\":[],\"mappings\":\"\"}}]}";
        SourceMapRegistry.register("startup_scripts/sections.ts", sections);
        OriginalPosition p = SourceMapRegistry.getMappedPosition("startup_scripts/sections.ts", 1, 0);
        assertNull(p.path, "indexed 'sections' format is unsupported -> passthrough");
    }

    @Test
    void malformedJsonIsRejectedAsPassthrough() {
        SourceMapRegistry.register("startup_scripts/bad.ts", "{not valid json");
        OriginalPosition p = SourceMapRegistry.getMappedPosition("startup_scripts/bad.ts", 1, 0);
        assertNull(p.path, "malformed source map -> passthrough");
    }

    @Test
    void blankJsonIsRejectedAsPassthrough() {
        SourceMapRegistry.register("startup_scripts/blank.ts", "");
        OriginalPosition p = SourceMapRegistry.getMappedPosition("startup_scripts/blank.ts", 1, 0);
        assertNull(p.path, "blank source map -> passthrough");
    }

    private static String minimalMap(String source) {
        return "{\"version\":3,\"file\":\"generated.js\",\"sourceRoot\":\"\",\"sources\":[\""
                + source + "\"],\"names\":[],\"mappings\":\"AAAA\"}";
    }
}
