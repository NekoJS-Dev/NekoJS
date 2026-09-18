package com.tkisor.nekojs.core.error;

import com.tkisor.nekojs.core.error.SourceMapRegistry.OriginalPosition;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
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
    private SourceMapRegistry registry;

    @BeforeAll
    static void bindPaths() {
        TestPlatformInit.ensureInitialized();
    }

    @BeforeEach
    void clearRegistry() {
        registry = new SourceMapRegistry(NekoJSPaths.get().root());
    }

    @Test
    void exactMatchResolvesOriginalPosition() {
        registry.register("startup_scripts/foo.ts", minimalMap("startup_scripts/foo.ts"));
        OriginalPosition p = registry.getMappedPosition("startup_scripts/foo.ts", 1, 0);
        assertNotNull(p.path, "exact key must resolve to a source path");
    }

    @Test
    void suffixOnlyQueryDoesNotResolve() {
        // RISK-C3: the old endsWith fallback was removed; only exact normalized keys match.
        registry.register("startup_scripts/foo.ts", minimalMap("startup_scripts/foo.ts"));
        OriginalPosition p = registry.getMappedPosition("other/foo.ts", 1, 0);
        assertNull(p.path, "a non-exact suffix query must not resolve");
        assertEquals(1, p.line, "miss should pass through the requested line");
    }

    @Test
    void sectionsFormatIsRejectedAsPassthrough() {
        String sections = "{\"version\":3,\"sections\":[{\"offset\":{\"line\":0,\"column\":0},"
                + "\"map\":{\"version\":3,\"sources\":[\"x\"],\"names\":[],\"mappings\":\"\"}}]}";
        registry.register("startup_scripts/sections.ts", sections);
        OriginalPosition p = registry.getMappedPosition("startup_scripts/sections.ts", 1, 0);
        assertNull(p.path, "indexed 'sections' format is unsupported -> passthrough");
    }

    @Test
    void malformedJsonIsRejectedAsPassthrough() {
        registry.register("startup_scripts/bad.ts", "{not valid json");
        OriginalPosition p = registry.getMappedPosition("startup_scripts/bad.ts", 1, 0);
        assertNull(p.path, "malformed source map -> passthrough");
    }

    @Test
    void blankJsonIsRejectedAsPassthrough() {
        registry.register("startup_scripts/blank.ts", "");
        OriginalPosition p = registry.getMappedPosition("startup_scripts/blank.ts", 1, 0);
        assertNull(p.path, "blank source map -> passthrough");
    }

    @Test
    void independentRuntimeRegistriesDoNotShareOrClearMappings() {
        SourceMapRegistry other = new SourceMapRegistry(registry.root());
        String first = mapWithContent("server_scripts/shared.ts", "first runtime");
        String second = mapWithContent("server_scripts/shared.ts", "second runtime");
        registry.register("server_scripts/shared.ts", first);
        other.register("server_scripts/shared.ts", second);

        assertEquals("first runtime", registry.getMappedPosition("server_scripts/shared.ts", 1, 1).sourceContent);
        assertEquals("second runtime", other.getMappedPosition("server_scripts/shared.ts", 1, 1).sourceContent);

        registry.clear();

        assertNull(registry.getMappedPosition("server_scripts/shared.ts", 1, 1).path);
        assertEquals("second runtime", other.getMappedPosition("server_scripts/shared.ts", 1, 1).sourceContent);
    }

    private static String minimalMap(String source) {
        return "{\"version\":3,\"file\":\"generated.js\",\"sourceRoot\":\"\",\"sources\":[\""
                + source + "\"],\"names\":[],\"mappings\":\"AAAA\"}";
    }

    private static String mapWithContent(String source, String content) {
        return "{\"version\":3,\"file\":\"generated.js\",\"sourceRoot\":\"\",\"sources\":[\""
                + source + "\"],\"sourcesContent\":[\"" + content
                + "\"],\"names\":[],\"mappings\":\"AAAA\"}";
    }
}
