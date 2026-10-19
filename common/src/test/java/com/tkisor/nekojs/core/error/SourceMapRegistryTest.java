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

    @Test
    void denseMappingsKeepColumnsAndEmptyGeneratedLines() {
        String source = "server_scripts/dense.ts";
        String map = minimalMap(source).replace("AAAA", "AAAA" + ",CAAC".repeat(4096) + ";;AAEA;");
        registry.register(source, map);

        assertEquals(4097, registry.getMappedPosition(source, 1, 4097).column);
        assertNull(registry.getMappedPosition(source, 2, 1).path);
        OriginalPosition next = registry.getMappedPosition(source, 3, 1);
        assertEquals(3, next.line);
        assertEquals(4097, next.column);
        assertNull(registry.getMappedPosition(source, 4, 1).path);
    }

    @Test
    void malformedSegmentDoesNotApplyItsPartialDeltas() {
        String source = "server_scripts/invalid-segment.ts";
        registry.register(source, minimalMap(source).replace("AAAA", "AAAA,CCEE?,CAAC"));

        OriginalPosition mapped = registry.getMappedPosition(source, 1, 2);
        assertNotNull(mapped.path);
        assertEquals(1, mapped.line);
        assertEquals(2, mapped.column);
    }

    @Test
    void incompleteTrailingFieldAndExtraFieldsKeepExistingTolerance() {
        String source = "server_scripts/tolerated-segment.ts";
        registry.register(source, minimalMap(source).replace("AAAA", "AAAAAA,CAACg"));

        OriginalPosition mapped = registry.getMappedPosition(source, 1, 2);
        assertNotNull(mapped.path);
        assertEquals(1, mapped.line);
        assertEquals(2, mapped.column);
    }

    @Test
    void sourceColumnAndNameDeltasContinueAcrossGeneratedLines() {
        String file = "server_scripts/deltas.ts";
        registry.register(file, """
                {"version":3,"sources":["one.ts","two.ts"],"names":["first","second"],
                 "mappings":"AAAAA,CCAEC;CDADD"}
                """);

        OriginalPosition second = registry.getMappedPosition(file, 1, 2);
        assertTrue(second.path.endsWith("two.ts"));
        assertEquals(3, second.column);
        assertEquals("second", second.name);
        OriginalPosition first = registry.getMappedPosition(file, 2, 2);
        assertTrue(first.path.endsWith("one.ts"));
        assertEquals(2, first.column);
        assertEquals("first", first.name);
    }

    @Test
    void nonMonotonicColumnsKeepOrderedSearchAndFirstEntryFallback() {
        String source = "server_scripts/non-monotonic.ts";
        registry.register(source, minimalMap(source).replace("AAAA", "EAAI,DAAC,AAAC"));

        // A query before the first generated column keeps the first segment, even when a
        // later segment has a smaller column. Duplicate matching columns keep the last one.
        assertEquals(5, registry.getMappedPosition(source, 1, 1).column);
        assertEquals(5, registry.getMappedPosition(source, 1, 2).column);
        assertEquals(7, registry.getMappedPosition(source, 1, 3).column);
    }

    @Test
    void extraFieldsKeepNameDeltasWithoutAddingMappingFields() {
        String source = "server_scripts/extra-fields.ts";
        registry.register(source, """
                {"version":3,"sources":["original.ts"],"names":["zero","one","two"],
                 "mappings":"AAAACE,AAAAC"}
                """);

        OriginalPosition mapped = registry.getMappedPosition(source, 1, 1);
        assertTrue(mapped.path.endsWith("original.ts"));
        assertEquals(1, mapped.line);
        assertEquals(1, mapped.column);
        assertEquals("two", mapped.name);
    }

    @Test
    void oversizedVlqKeepsExistingIntegerWrapAndOrderedSearch() {
        String source = "server_scripts/oversized-vlq.ts";
        registry.register(source, minimalMap(source).replace("AAAA", "AAAA,ggggggDAAC,igggggBAAA"));

        OriginalPosition mapped = registry.getMappedPosition(source, 1, 1);
        assertNotNull(mapped.path);
        assertEquals(1, mapped.line);
        assertEquals(2, mapped.column);
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
