package com.tkisor.nekojs.api.catalog;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.contract.NormativeApiContract;
import com.tkisor.nekojs.api.surface.ApiEnvironmentSnapshot;
import com.tkisor.nekojs.api.surface.ApiResolutionException;
import com.tkisor.nekojs.api.surface.ApiSignature;
import com.tkisor.nekojs.api.surface.ApiSymbol;
import com.tkisor.nekojs.api.surface.ApiSymbolId;
import com.tkisor.nekojs.api.surface.ApiTier;
import com.tkisor.nekojs.api.surface.EnvironmentKey;
import com.tkisor.nekojs.api.surface.LegacyGlobalReservation;
import com.tkisor.nekojs.api.surface.LoaderVersion;
import com.tkisor.nekojs.api.surface.RuntimeDist;
import com.tkisor.nekojs.api.surface.ScriptTypeId;
import com.tkisor.nekojs.core.api.CoreManagedApiBootstrap;
import com.tkisor.nekojs.core.api.FrozenApiRegistry;
import com.tkisor.nekojs.core.api.JsApiSurfaceResolver;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Characterization of the legacy observation side:
 * <ul>
 *   <li>{@link LegacySurfaceAdapter} converts legacy catalog symbols into observation entries
 *       ({@code NekoScriptCatalogSnapshot.legacySurface()}), but they do not enter the managed
 *       surface and get no managed stable identity;</li>
 *   <li>when names collide, the managed contract identity is kept as is (not overwritten, not
 *       merged with signatures); the resolver treats a legacy reserved name that shadows a managed
 *       global as an explicit {@code LEGACY_NAME_COLLISION} failure.</li>
 * </ul>
 * The test only pins existing behavior (characterization) and does not change what migration
 * observation does.
 */
class LegacySurfaceShadowCharacterizationTest {

    private static final URI TEST_CODE_SOURCE = URI.create("file:///test-nekojs-legacy-shadow.jar");

    /** A legacy binding with the same name as a managed global: it must appear on the observation side,
     *  but must not overwrite the managed contract. */
    private static final String MANAGED_NAME = "Text";

    @Test
    void legacySurfaceKeepsObservationEntryForManagedNamedSymbol() {
        BindingCatalogEntry legacyBinding = BindingCatalogEntry.of(
                MANAGED_NAME, ScriptType.SERVER, LegacyApiStub.class, true);
        List<ApiSymbol> legacy = LegacySurfaceAdapter.fromBindings(List.of(legacyBinding));

        assertEquals(1, legacy.size());
        assertEquals(ApiSymbolId.parse("global:" + MANAGED_NAME), legacy.getFirst().id(),
                "legacy binding maps to an observation id in the legacy surface");
        // Observation placeholder signature: a no-arg function (kind=global is migration metadata, not a
        // stable promise)
        assertEquals(List.of(ApiSignature.function(List.of(), com.tkisor.nekojs.api.surface.ApiTypeRef.voidType())),
                legacy.getFirst().signatures());
    }

    @Test
    void legacySurfaceNeverOverwritesManagedSymbolsInSnapshot() {
        ApiEnvironmentSnapshot managed = managedSnapshot();
        List<ApiSymbol> legacySurface = LegacySurfaceAdapter.fromBindings(List.of(
                BindingCatalogEntry.of(MANAGED_NAME, ScriptType.SERVER, LegacyApiStub.class, true)));

        NekoScriptCatalogSnapshot snapshot = new NekoScriptCatalogSnapshot(
                ScriptType.all(),
                List.of(BindingCatalogEntry.of(MANAGED_NAME, ScriptType.SERVER, LegacyApiStub.class, true)),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(),
                new TypeOutputLayout(Path.of("probe-types"), Path.of("snippets")),
                Map.of(ScriptType.SERVER, managed),
                legacySurface);

        // The managed surface is kept as is: no contract symbol is missing
        Set<String> contractIds = contractSymbolIds();
        Set<String> managedIds = snapshot.managedApis().get(ScriptType.SERVER).surfaceSnapshot()
                .symbols().stream().map(s -> s.id().value()).collect(Collectors.toSet());
        assertEquals(contractIds, managedIds,
                "legacy catalog entries must not add, remove or replace managed surface symbols");

        // global:Text is still a contract signature in the managed surface (an object global); the legacy
        // void signature does not overwrite it
        ApiSymbol managedText = symbolById(
                snapshot.managedApis().get(ScriptType.SERVER).surfaceSnapshot().symbols(), "global:" + MANAGED_NAME);
        assertNotNull(managedText);
        ApiSignature managedSig = managedText.signatures().getFirst();
        assertNotEquals(ApiSignature.function(List.of(), com.tkisor.nekojs.api.surface.ApiTypeRef.voidType()).callKey()
                        + ":" + com.tkisor.nekojs.api.surface.ApiTypeRef.voidType().compatibilityKey(),
                managedSig.callKey() + ":" + managedSig.returnType().compatibilityKey(),
                "managed global must not be shadowed by the legacy placeholder signature");

        // The legacy side stands alone: the observation entry lives in legacySurface, not in managedApis
        assertEquals(1, snapshot.legacySurface().size());
        assertEquals(ApiSymbolId.parse("global:" + MANAGED_NAME), snapshot.legacySurface().getFirst().id());
    }

    @Test
    void resolverRejectsLegacyReservationShadowingManagedGlobal() {
        com.tkisor.nekojs.api.contract.VerifiedApiContract contract =
                CoreManagedApiBootstrap.buildContract(TEST_CODE_SOURCE);
        com.tkisor.nekojs.api.contract.VerifiedContractSet contracts =
                com.tkisor.nekojs.api.contract.VerifiedContractSet.of(contract);
        com.tkisor.nekojs.core.api.CoreManagedApiBootstrap.CoreManagedApi core =
                CoreManagedApiBootstrap.load(new EmptyPlatform(), TEST_CODE_SOURCE);
        // Build a fresh set of contributions; the ones returned by load already bind the same contracts,
        // so they can be used directly
        ApiResolutionException thrown = org.junit.jupiter.api.Assertions.assertThrows(
                ApiResolutionException.class,
                () -> JsApiSurfaceResolver.resolve(
                        environment(), contracts, List.of(core.contributions()),
                        List.of(new LegacyGlobalReservation(MANAGED_NAME,
                                ApiSymbolId.parse("global:legacy-" + MANAGED_NAME)))));
        assertEquals("LEGACY_NAME_COLLISION", thrown.code(),
                "legacy shadowing a managed global must fail with LEGACY_NAME_COLLISION");
    }

    @Test
    void legacySymbolsStayOnObservationKindsAndPlaceholderSignatures() {
        NekoScriptCatalogSnapshot legacyOnly = new NekoScriptCatalogSnapshot(
                ScriptType.all(),
                List.of(BindingCatalogEntry.of("Item", ScriptType.SERVER, LegacyApiStub.class, true)),
                List.of(EventCatalogEntry.of("LegacyEvents", "tick", ScriptType.SERVER,
                        LegacyApiStub.class, null, false, false)),
                List.of(new AdapterCatalogEntry(LegacyApiStub.class,
                        List.of(com.tkisor.nekojs.api.AdapterInputShape.self()),
                        com.tkisor.nekojs.api.data.ConversionPrecedence.LOWEST, null)),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                new TypeOutputLayout(Path.of("probe-types"), Path.of("snippets")),
                Map.of(), List.of());
        List<ApiSymbol> legacy = LegacySurfaceAdapter.convert(legacyOnly);

        Set<String> kinds = legacy.stream().map(s -> s.id().kind()).collect(Collectors.toSet());
        assertEquals(Set.of("global", "event", "adapter"), kinds,
                "legacy observation ids use observation kinds, not managed stable kinds");

        for (ApiSymbol symbol : legacy) {
            // Observation placeholder signature: no parameters, void return; it makes no managed contract promise
            assertEquals(1, symbol.signatures().size());
            assertEquals(List.of(), symbol.signatures().getFirst().parameters());
        }
        // LEGACY_PREVIEW is still part of the tier vocabulary (reserved for the contribution layer);
        // the managed surface does not consume it
        assertNotNull(ApiTier.valueOf("LEGACY_PREVIEW"));
    }

    // ---------- helpers ----------

    private static Set<String> contractSymbolIds() {
        NormativeApiContract contract = CoreManagedApiBootstrap.buildContract(TEST_CODE_SOURCE).contract();
        return contract.symbols().stream().map(s -> s.id().value()).collect(Collectors.toSet());
    }

    private static ApiSymbol symbolById(List<ApiSymbol> symbols, String id) {
        return symbols.stream().filter(s -> s.id().value().equals(id)).findFirst().orElse(null);
    }

    private static ApiEnvironmentSnapshot managedSnapshot() {
        com.tkisor.nekojs.api.contract.VerifiedApiContract contract =
                CoreManagedApiBootstrap.buildContract(TEST_CODE_SOURCE);
        com.tkisor.nekojs.api.contract.VerifiedContractSet contracts =
                com.tkisor.nekojs.api.contract.VerifiedContractSet.of(contract);
        com.tkisor.nekojs.core.api.CoreManagedApiBootstrap.CoreManagedApi core =
                CoreManagedApiBootstrap.load(new EmptyPlatform(), TEST_CODE_SOURCE);
        FrozenApiRegistry registry = JsApiSurfaceResolver.resolve(
                environment(), contracts, List.of(core.contributions()), List.of());
        return registry.environmentSnapshot();
    }

    private static EnvironmentKey environment() {
        return new EnvironmentKey(
                ScriptTypeId.SERVER,
                RuntimeDist.DEDICATED_SERVER,
                "test",
                "0.0.0",
                LoaderVersion.parse("0.0.0"),
                "1.21.1",
                Map.of());
    }

    /** Minimal host class for the legacy observation tests. */
    public static final class LegacyApiStub {
        public String legacyOnlyMethod() { return "legacy"; }
    }

    private static final class EmptyPlatform implements com.tkisor.nekojs.platform.IPlatform {
        @Override public boolean isClient() { return false; }
        @Override public boolean isDevelopment() { return true; }
        @Override public String getMcVersion() { return "1.21.1"; }
        @Override public Path getGameDir() { return Path.of("."); }
        @Override public Map<String, com.tkisor.nekojs.platform.IModInfo> getMods() { return Map.of(); }
        @Override public com.tkisor.nekojs.platform.IModInfo getInfo(String modID) { return null; }
        @Override public String getLoaderId() { return "test"; }
        @Override public String getLoaderVersion() { return "0.0.0"; }
    }
}
