package com.tkisor.nekojs.core.api;

import com.tkisor.nekojs.api.contract.VerifiedApiContract;
import com.tkisor.nekojs.api.surface.ApiManifest;
import com.tkisor.nekojs.api.surface.ApiRuntimeVersions;
import com.tkisor.nekojs.api.surface.ApiSurfaceSnapshot;
import com.tkisor.nekojs.api.surface.EnvironmentKey;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Frozen API surface baseline (golden): the core API surface produced by reflecting the
 * current contracts, stored in
 * {@code src/test/resources/nekojs/golden/api-manifest-core.json}.
 *
 * <p>Any change to a symbol, signature, capability or module fails the golden comparison —
 * this is the machine-checkable part of "frozen": a change must explicitly run with
 * `-Dnekojs.golden.regenerate=true` to regenerate, and then pass breaking-change
 * review (governance details live in the local, unversioned ai_arch/API_VERSIONING.md).
 * Never change the API surface silently.
 */
class ApiManifestGoldenTest {

    private static final URI TEST_CODE_SOURCE = URI.create("file:///test-nekojs.jar");
    private static final Path GOLDEN_PATH = Path.of("src/test/resources/nekojs/golden/api-manifest-core.json");

    @Test
    void coreApiSurfaceMatchesFrozenGolden() throws Exception {
        ApiManifest manifest = buildManifest();
        String actual = ApiManifestJson.toJson(manifest) + "\n";

        // Boolean.getBoolean, not != null: :common:test always passes this system property
        // through as the string "false" (common/build.gradle), so != null would rewrite the
        // baseline on every ordinary test run and then Assumptions.abort, leaving the whole
        // freeze gate a no-op.
        if (Boolean.getBoolean("nekojs.golden.regenerate")) {
            Files.createDirectories(GOLDEN_PATH.getParent());
            Files.writeString(GOLDEN_PATH, actual);
            Assumptions.abort("golden regenerated, review the diff before committing");
        }

        String expected = Files.readString(GOLDEN_PATH);
        assertEquals(expected, actual, "API surface drifted from frozen baseline; "
                + "run `./gradlew :common:test -Dnekojs.golden.regenerate=true --tests ...ApiManifestGoldenTest` "
                + "to regenerate, then review the diff (breaking-change review required)");
    }

    @Test
    void manifestCarriesVersionAndPlatformMetadata() {
        ApiManifest manifest = buildManifest();

        // api/spi/runtime versions come from api-runtime.properties (0.0.0 = not gated)
        assertEquals(ApiRuntimeVersionReader.read().apiVersion().toString(), manifest.apiVersion());
        assertEquals("0.0.0", manifest.spiVersion());
        assertEquals(1, manifest.catalogSchemaVersion());
        assertEquals("test", manifest.platform().loader());
        // deterministic ordering
        List<String> ids = manifest.symbols().stream().map(ApiManifest.ManifestSymbol::id).toList();
        assertEquals(ids.stream().sorted().toList(), ids, "symbols must be sorted deterministically");
    }

    @Test
    void frozenBaselineCoversCoreFacades() throws Exception {
        ApiManifest manifest = buildManifest();
        List<String> ids = manifest.symbols().stream().map(ApiManifest.ManifestSymbol::id).toList();

        assertTrue(ids.contains("global:Platform"), "Platform global must be frozen: " + ids);
        assertTrue(ids.contains("member:JsonIO.parse"), "JsonIO.parse must be frozen");
        assertTrue(ids.contains("member:Text.of"), "Text.of must be frozen");
        assertTrue(ids.contains("member:NBT.parse"), "NBT.parse must be frozen");
        // baseline for the total symbol count of the contract reflection (the original
        // JSON had 110; reflection may be more complete)
        assertTrue(ids.size() >= 110, "frozen symbol count below baseline: " + ids.size());
    }

    /**
     * Negative assertion: the freeze gate must really detect surface changes. Removing a
     * symbol, adding a symbol, or adding an overload signature to a symbol must all change
     * the serialized result away from the golden — otherwise
     * {@link #coreApiSurfaceMatchesFrozenGolden} only compares two identical strings.
     */
    @Test
    void goldenRejectsRemovedAddedAndChangedSymbols() throws Exception {
        String golden = Files.readString(GOLDEN_PATH);
        ApiManifest manifest = buildManifest();
        List<ApiManifest.ManifestSymbol> symbols = manifest.symbols();
        assertTrue(symbols.size() >= 2, "need at least two symbols to mutate");

        assertNotEquals(golden, json(withSymbols(manifest, symbols.subList(1, symbols.size()))),
                "removing a symbol must change the manifest JSON");

        List<ApiManifest.ManifestSymbol> added = new ArrayList<>(symbols);
        added.add(new ApiManifest.ManifestSymbol("global:zzzNotFrozen", List.of()));
        assertNotEquals(golden, json(withSymbols(manifest, added)),
                "adding a symbol must change the manifest JSON");

        List<ApiManifest.ManifestSymbol> changed = new ArrayList<>(symbols);
        ApiManifest.ManifestSymbol first = changed.get(0);
        List<String> extraSignature = new ArrayList<>(first.signatures());
        extraSignature.add("(java.lang.String)");
        changed.set(0, new ApiManifest.ManifestSymbol(first.id(), extraSignature));
        assertNotEquals(golden, json(withSymbols(manifest, changed)),
                "adding an overload signature must change the manifest JSON");
    }

    /** Regeneration may only turn on for an explicit true: build.gradle always passes the string "false". */
    @Test
    void regenerateSwitchIsOffForNonTrueValues() {
        assertFalse(Boolean.getBoolean("nekojs.golden.regenerate"),
                "golden regeneration must be off during a normal test run; the gate is a no-op otherwise");
    }

    private static String json(ApiManifest manifest) {
        return ApiManifestJson.toJson(manifest) + "\n";
    }

    private static ApiManifest withSymbols(ApiManifest base, List<ApiManifest.ManifestSymbol> symbols) {
        return new ApiManifest(base.catalogSchemaVersion(), base.apiVersion(), base.spiVersion(),
                base.runtimeContractVersion(), base.nekojsVersion(), base.platform(),
                base.capabilities(), base.modules(), symbols);
    }

    private static ApiManifest buildManifest() {
        VerifiedApiContract contract = CoreManagedApiBootstrap.buildContract(TEST_CODE_SOURCE);
        ApiRuntimeVersions versions = ApiRuntimeVersionReader.read();
        ApiSurfaceSnapshot surface = new ApiSurfaceSnapshot(
                contract.contract().symbols(),
                Set.of(),
                List.of(),
                List.of(),
                new EnvironmentKey(null, null, null, null, null, null, Map.of()));
        return ApiManifestGenerator.generate(versions, "test", "0.0.0", surface);
    }
}
