package com.tkisor.nekojs.api.surface;

import com.tkisor.nekojs.api.capability.CapabilityDefinition;
import com.tkisor.nekojs.api.capability.CapabilityImplementationMode;
import com.tkisor.nekojs.api.contract.ApiContractIdentity;
import com.tkisor.nekojs.api.contract.ApiContractKind;
import com.tkisor.nekojs.api.module.*;
import com.tkisor.nekojs.api.plugin.PluginIdentity;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ApiDescriptorModelTest {

    @Test
    void loaderVersionParsesRealNeoForgeVersions() {
        LoaderVersion v1 = LoaderVersion.parse("26.1.2.36-beta");
        assertEquals(26, v1.segments().get(0));
        assertEquals(1, v1.segments().get(1));
        assertEquals(2, v1.segments().get(2));
        assertEquals(36, v1.segments().get(3));
        assertEquals("beta", v1.qualifier());

        LoaderVersion v2 = LoaderVersion.parse("26.2.0.7-beta");
        assertEquals(26, v2.segments().get(0));
        assertEquals(2, v2.segments().get(1));
        assertEquals(0, v2.segments().get(2));
        assertEquals(7, v2.segments().get(3));
        assertEquals("beta", v2.qualifier());

        LoaderVersion v3 = LoaderVersion.parse("0.5.14-alpha");
        assertEquals(0, v3.segments().get(0));
        assertEquals(5, v3.segments().get(1));
        assertEquals(14, v3.segments().get(2));
        assertEquals("alpha", v3.qualifier());
    }

    @Test
    void loaderVersionComparisonMissingSegmentsDefaultZero() {
        LoaderVersion short1 = LoaderVersion.parse("1.0");
        LoaderVersion long1 = LoaderVersion.parse("1.0.0.0");
        assertEquals(0, short1.compareTo(long1));
    }

    @Test
    void loaderVersionPrereleaseBelowRelease() {
        LoaderVersion pre = LoaderVersion.parse("1.0.0-beta");
        LoaderVersion rel = LoaderVersion.parse("1.0.0");
        assertTrue(pre.compareTo(rel) < 0);
    }

    @Test
    void loaderVersionQualifierComparisonByUnicode() {
        LoaderVersion alpha = LoaderVersion.parse("1.0.0-alpha");
        LoaderVersion beta = LoaderVersion.parse("1.0.0-beta");
        assertTrue(alpha.compareTo(beta) < 0);
    }

    @Test
    void loaderVersionRangeExact() {
        LoaderVersion v = LoaderVersion.parse("26.1.2.36-beta");
        LoaderVersionRange range = LoaderVersionRange.exact(v);
        assertTrue(range.matches(v));
        assertFalse(range.matches(LoaderVersion.parse("26.1.2.37-beta")));
    }

    @Test
    void loaderVersionRangeMinMax() {
        LoaderVersionRange range = LoaderVersionRange.range(
                LoaderVersion.parse("26.1.0.0"),
                LoaderVersion.parse("26.3.0.0"));
        assertTrue(range.matches(LoaderVersion.parse("26.2.0.7-beta")));
        assertFalse(range.matches(LoaderVersion.parse("26.3.0.0")));
        assertFalse(range.matches(LoaderVersion.parse("26.0.9.9")));
    }

    @Test
    void environmentScopeMatchesKey() {
        EnvironmentScope scope = new EnvironmentScope(
                null,
                RuntimeDist.DEDICATED_SERVER,
                Set.of(),
                Set.of("neoforge"),
                LoaderVersionRange.range(
                        LoaderVersion.parse("26.0.0"),
                        LoaderVersion.parse("27.0.0")),
                null
        );
        EnvironmentKey key = new EnvironmentKey(
                null,
                RuntimeDist.DEDICATED_SERVER,
                "neoforge",
                "26.2.0.7-beta",
                LoaderVersion.parse("26.2.0.7-beta"),
                "1.21.1",
                Map.of()
        );
        assertTrue(scope.matches(key));
    }

    @Test
    void environmentScopeRejectsMismatchedDist() {
        EnvironmentScope scope = new EnvironmentScope(
                null,
                RuntimeDist.CLIENT,
                Set.of(),
                Set.of("neoforge"),
                null,
                null
        );
        EnvironmentKey key = new EnvironmentKey(
                null,
                RuntimeDist.DEDICATED_SERVER,
                "neoforge",
                "26.2.0.7-beta",
                LoaderVersion.parse("26.2.0.7-beta"),
                "1.21.1",
                Map.of()
        );
        assertFalse(scope.matches(key));
    }

    @Test
    void moduleDescriptorTierDiscrimination() {
        ApiContractIdentity contract = new ApiContractIdentity(
                "nekojs", ApiContractKind.PORTABLE, "nekojs:api", ApiVersion.parse("0.0.0"));

        // FEATURE must have contractVersion
        assertDoesNotThrow(() -> new ApiModuleDescriptor(
                "test-feature", ApiTier.FEATURE, ApiVersion.parse("0.0.0"), 0, contract, List.of()));

        // VERSION must have positive moduleRevision
        assertDoesNotThrow(() -> new ApiModuleDescriptor(
                "test-version", ApiTier.VERSION, null, 1, null, List.of()));

        // VERSION with zero revision is invalid
        assertThrows(IllegalArgumentException.class, () -> new ApiModuleDescriptor(
                "test-version-bad", ApiTier.VERSION, null, 0, null, List.of()));
    }

    @Test
    void moduleDescriptorFeatureRequiresContractVersion() {
        assertThrows(IllegalArgumentException.class, () -> new ApiModuleDescriptor(
                "test-feature", ApiTier.FEATURE, null, 0, null, List.of()));
    }

    @Test
    void inactiveReasonHasFiveValues() {
        assertEquals(5, InactiveReason.values().length);
        assertNotNull(InactiveReason.SCOPE_MISMATCH);
        assertNotNull(InactiveReason.CAPABILITY_UNAVAILABLE);
        assertNotNull(InactiveReason.MISSING_MODULE_DEPENDENCY);
        assertNotNull(InactiveReason.DEPENDENCY_INACTIVE);
        assertNotNull(InactiveReason.MODULE_VERSION_MISMATCH);
    }

    @Test
    void capabilityDefinitionCreation() {
        CapabilityDefinition def = new CapabilityDefinition(
                "js-engine",
                CapabilityImplementationMode.SINGLE,
                Set.of("graaljs"));
        assertEquals("js-engine", def.name());
        assertEquals(CapabilityImplementationMode.SINGLE, def.mode());
        assertEquals(Set.of("graaljs"), def.providerHints());
    }

    @Test
    void contractKindHasFiveValues() {
        assertEquals(5, ApiContractKind.values().length);
        assertNotNull(ApiContractKind.PORTABLE);
        assertNotNull(ApiContractKind.FEATURE);
        assertNotNull(ApiContractKind.PLATFORM);
        assertNotNull(ApiContractKind.ADDON);
        assertNotNull(ApiContractKind.SPI);
    }

    @Test
    void pluginIdentityEquality() {
        PluginIdentity id1 = new PluginIdentity("nekojs", "core");
        PluginIdentity id2 = new PluginIdentity("nekojs", "core");
        assertEquals(id1, id2);
        assertEquals(id1.hashCode(), id2.hashCode());
    }

    @Test
    void legacyGlobalReservation() {
        LegacyGlobalReservation reservation = new LegacyGlobalReservation(
                "Item", ApiSymbolId.parse("global:Item"));
        assertEquals("Item", reservation.globalName());
        assertEquals("global:Item", reservation.targetId().value());
    }

    @Test
    void apiRuntimeVersionsPhase1Defaults() {
        ApiRuntimeVersions versions = new ApiRuntimeVersions(
                "0.1.0", ApiVersion.parse("0.0.0"),
                ApiVersion.parse("0.0.0"), ApiVersion.parse("0.0.0"), 1);
        assertEquals("0.1.0", versions.nekojsVersion());
        assertEquals(ApiVersion.parse("0.0.0"), versions.apiVersion());
        assertEquals(ApiVersion.parse("0.0.0"), versions.spiVersion());
        assertEquals(ApiVersion.parse("0.0.0"), versions.runtimeContractVersion());
        assertEquals(1, versions.catalogSchemaVersion());
    }
}
