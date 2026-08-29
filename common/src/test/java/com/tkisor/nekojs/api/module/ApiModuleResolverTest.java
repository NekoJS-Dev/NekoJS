package com.tkisor.nekojs.api.module;

import com.tkisor.nekojs.api.capability.ActiveCapability;
import com.tkisor.nekojs.api.capability.CapabilityImplementationMode;
import com.tkisor.nekojs.api.contract.ApiContractIdentity;
import com.tkisor.nekojs.api.contract.ApiContractKind;
import com.tkisor.nekojs.api.surface.ApiResolutionException;
import com.tkisor.nekojs.api.surface.ApiTier;
import com.tkisor.nekojs.api.surface.ApiVersion;
import com.tkisor.nekojs.api.surface.ApiVersionRange;
import com.tkisor.nekojs.api.surface.EnvironmentKey;
import com.tkisor.nekojs.api.surface.LoaderVersion;
import com.tkisor.nekojs.api.surface.RuntimeDist;
import com.tkisor.nekojs.api.surface.ScriptTypeId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ApiModuleResolverTest {

    private static EnvironmentKey serverEnv() {
        return new EnvironmentKey(
                ScriptTypeId.SERVER,
                RuntimeDist.DEDICATED_SERVER,
                "neoforge",
                "21.1.0",
                LoaderVersion.parse("21.1.0"),
                "1.21.1",
                Map.of());
    }

    private static ApiModuleDescriptor module(String id, ApiModuleDependency... deps) {
        return new ApiModuleDescriptor(
                id,
                ApiTier.FEATURE,
                ApiVersion.parse("1.0.0"),
                0,
                new ApiContractIdentity("test-owner", ApiContractKind.FEATURE, id, ApiVersion.parse("1.0.0")),
                List.of(deps));
    }

    private static ApiModuleDependency dependsOnFeature(String moduleId, String versionRange) {
        return new ApiModuleDependency(moduleId, ApiVersionRange.range(
                ApiVersion.parse(versionRange.split(",")[0].replace("[", "").replace(")", "")),
                ApiVersion.parse(versionRange.split(",")[1].replace(")", "").replace("]", ""))),
                ApiModuleDependency.DependencyType.MODULE);
    }

    private static ApiModuleDependency requiresCapability(String capName, String versionRange) {
        return new ApiModuleDependency(capName, ApiVersionRange.range(
                ApiVersion.parse(versionRange.split(",")[0].replace("[", "").replace(")", "")),
                ApiVersion.parse(versionRange.split(",")[1].replace(")", "").replace("]", ""))),
                ApiModuleDependency.DependencyType.CAPABILITY);
    }

    @Test
    void inactiveDependencyPropagatesThroughAllDependents() {
        // C requires capability "missing" -> CAPABILITY_UNAVAILABLE
        // B depends on C -> DEPENDENCY_INACTIVE
        // A depends on B -> DEPENDENCY_INACTIVE
        ModuleResolution result = ApiModuleResolver.resolve(
                serverEnv(),
                ApiVersion.parse("1.0.0"),
                List.of(
                        module("C", requiresCapability("missing", "[1.0.0,2.0.0)")),
                        module("B", dependsOnFeature("C", "[1.0.0,2.0.0)")),
                        module("A", dependsOnFeature("B", "[1.0.0,2.0.0)"))),
                List.of()); // no capabilities available

        assertEquals(InactiveReason.CAPABILITY_UNAVAILABLE, result.inactive("C").orElseThrow().reason());
        assertEquals(InactiveReason.DEPENDENCY_INACTIVE, result.inactive("B").orElseThrow().reason());
        assertEquals(InactiveReason.DEPENDENCY_INACTIVE, result.inactive("A").orElseThrow().reason());
        assertTrue(result.activeIds().isEmpty());
    }

    @Test
    void readyNodesUseCodePointOrder() {
        // Both modules have no dependencies, should be sorted by code-point
        ModuleResolution result = ApiModuleResolver.resolve(
                serverEnv(),
                ApiVersion.parse("1.0.0"),
                List.of(module("@x/z"), module("@x/a")),
                List.of());

        assertEquals(List.of("@x/a", "@x/z"), result.activeIds());
    }

    @Test
    void singleModuleActivates() {
        ModuleResolution result = ApiModuleResolver.resolve(
                serverEnv(),
                ApiVersion.parse("1.0.0"),
                List.of(module("test-module")),
                List.of());

        assertEquals(1, result.activeIds().size());
        assertEquals("test-module", result.activeIds().getFirst());
        assertTrue(result.inactive().isEmpty());
    }

    @Test
    void emptyDescriptorsReturnsEmptyResult() {
        ModuleResolution result = ApiModuleResolver.resolve(
                serverEnv(),
                ApiVersion.parse("1.0.0"),
                List.of(),
                List.of());

        assertTrue(result.activeIds().isEmpty());
        assertTrue(result.inactive().isEmpty());
    }

    @Test
    void dependencyChainResolvesInOrder() {
        // C -> B -> A (A depends on B, B depends on C)
        // All should be active in dependency-first order: C, B, A
        ModuleResolution result = ApiModuleResolver.resolve(
                serverEnv(),
                ApiVersion.parse("1.0.0"),
                List.of(
                        module("A", dependsOnFeature("B", "[1.0.0,2.0.0)")),
                        module("B", dependsOnFeature("C", "[1.0.0,2.0.0)")),
                        module("C")),
                List.of());

        assertEquals(List.of("C", "B", "A"), result.activeIds());
    }

    @Test
    void missingDependencyCausesInactive() {
        // A depends on B, but B doesn't exist
        ModuleResolution result = ApiModuleResolver.resolve(
                serverEnv(),
                ApiVersion.parse("1.0.0"),
                List.of(module("A", dependsOnFeature("B", "[1.0.0,2.0.0)"))),
                List.of());

        assertEquals(InactiveReason.MISSING_MODULE_DEPENDENCY, result.inactive("A").orElseThrow().reason());
    }

    @Test
    void capabilityWithCorrectVersionActivatesModule() {
        // Module requires capability "test-cap" with version range [1.0.0,2.0.0)
        // Capability is available with version 1.0.0
        ActiveCapability cap = new ActiveCapability(
                "test-cap",
                CapabilityImplementationMode.SINGLE,
                "cap-impl");

        ModuleResolution result = ApiModuleResolver.resolve(
                serverEnv(),
                ApiVersion.parse("1.0.0"),
                List.of(module("test-module", requiresCapability("test-cap", "[1.0.0,2.0.0)"))),
                List.of(cap));

        assertEquals(1, result.activeIds().size());
        assertEquals("test-module", result.activeIds().getFirst());
    }

    @Test
    void duplicateModuleDescriptorsFail() {
        assertThrows(ApiResolutionException.class,
                () -> ApiModuleResolver.resolve(
                        serverEnv(),
                        ApiVersion.parse("1.0.0"),
                        List.of(module("dup"), module("dup")),
                        List.of()));
    }
}
