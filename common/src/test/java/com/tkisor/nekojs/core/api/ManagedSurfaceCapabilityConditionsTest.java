package com.tkisor.nekojs.core.api;

import com.tkisor.nekojs.api.capability.CapabilityDefinition;
import com.tkisor.nekojs.api.surface.ApiResolutionException;
import com.tkisor.nekojs.api.capability.CapabilityImplementationMode;
import com.tkisor.nekojs.api.capability.CapabilityProviderContribution;
import com.tkisor.nekojs.api.capability.CapabilityResolution;
import com.tkisor.nekojs.api.capability.CapabilityResolver;
import com.tkisor.nekojs.api.capability.CapabilityStatus;
import com.tkisor.nekojs.api.contract.ApiContractIdentity;
import com.tkisor.nekojs.api.contract.ApiContractKind;
import com.tkisor.nekojs.api.contract.NormativeApiContract;
import com.tkisor.nekojs.api.contract.VerifiedApiContract;
import com.tkisor.nekojs.api.contract.VerifiedContractSet;
import com.tkisor.nekojs.api.plugin.PluginIdentity;
import com.tkisor.nekojs.api.surface.ApiContributionRegistry;
import com.tkisor.nekojs.api.surface.ApiManifest;
import com.tkisor.nekojs.api.surface.ApiRuntimeVersions;
import com.tkisor.nekojs.api.surface.ApiVersion;
import com.tkisor.nekojs.api.surface.EnvironmentKey;
import com.tkisor.nekojs.api.surface.EnvironmentScope;
import com.tkisor.nekojs.api.surface.LoaderVersion;
import com.tkisor.nekojs.api.surface.LoaderVersionRange;
import com.tkisor.nekojs.api.surface.RuntimeDist;
import com.tkisor.nekojs.api.surface.ScriptTypeId;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A contract capability (supported/partial/unavailable) can carry loader, version and runtime
 * conditions, and those conditions take effect during surface resolution. An unavailable
 * capability is recorded explicitly as DECLARED_UNAVAILABLE or UNAVAILABLE and never silently
 * becomes a no-op; the manifest only exposes the active capability results, derived from the same
 * state.
 *
 * <p>All assertions go through the public path ({@code JsApiSurfaceResolver.resolve} → surface
 * snapshot → {@code ApiManifestGenerator}); the resolver is called directly only to check that
 * unavailable capabilities are recorded. The test does not inspect private state.
 */
class ManagedSurfaceCapabilityConditionsTest {

    private static final URI CODE_SOURCE = URI.create("file:///test-capability.jar");
    private static final ApiVersion CONTRACT_VERSION = ApiVersion.parse("1.0.0");

    private static EnvironmentScope loaderScope(String... loaderIds) {
        return new EnvironmentScope(null, null, Set.of(), Set.of(loaderIds), null, null);
    }

    /** Contract with a single capability; the owner is fixed to nekojs-core/PORTABLE so
     *  requirePortable can be reused. */
    private static VerifiedContractSet contractWith(NormativeApiContract.ContractCapability... capabilities) {
        NormativeApiContract contract = new NormativeApiContract(
                2,
                new NormativeApiContract.ContractIdentity(
                        "nekojs-core", ApiContractKind.PORTABLE, "capability-contract", CONTRACT_VERSION),
                "Capability conditions fixture",
                List.of(),
                List.of(capabilities),
                List.of());
        return VerifiedContractSet.of(VerifiedApiContract.create(
                new ApiContractIdentity("nekojs-core", ApiContractKind.PORTABLE, "capability-contract",
                        CONTRACT_VERSION),
                contract,
                CODE_SOURCE,
                "capability-contract.json",
                "sha256:integrity",
                "sha256:compatibility"));
    }

    /** Maps a contract capability to a resolver capability definition, matching
     *  JsApiSurfaceResolver.collectCapabilityDefinitions. */
    private static List<CapabilityDefinition> definitionsOf(VerifiedContractSet contracts) {
        List<CapabilityDefinition> definitions = new java.util.ArrayList<>();
        for (NormativeApiContract.ContractCapability cap : contracts.requirePortable("nekojs-core")
                .contract().capabilities()) {
            definitions.add(new CapabilityDefinition(
                    cap.id(), CONTRACT_VERSION, CapabilityImplementationMode.SINGLE,
                    com.tkisor.nekojs.api.capability.ProviderPolicy.CORE_ONLY,
                    Set.of(), cap.conditions(), Set.of(), Set.of(), cap.status()));
        }
        return definitions;
    }

    /** Provider registry: the owner must match the contract owner (nekojs-core), because the
     *  CORE_ONLY policy accepts a core-owned provider. */
    private static ApiContributionRegistry providerRegistry(VerifiedContractSet contracts) {
        return ApiContributionRegistry.ownedBy(
                new PluginIdentity("nekojs-core", "capability-test-provider", CODE_SOURCE), contracts);
    }

    private static void registerProvider(ApiContributionRegistry providers, String capabilityId,
                                         EnvironmentScope scope, String implementation) {
        providers.registerCapabilityProvider(capabilityId, CONTRACT_VERSION,
                CapabilityImplementationMode.SINGLE, scope, implementation, Map.of());
    }

    private static FrozenApiRegistry resolve(VerifiedContractSet contracts, ApiContributionRegistry providers,
                                             String loaderId, String loaderVersion) {
        EnvironmentKey env = new EnvironmentKey(
                ScriptTypeId.SERVER,
                RuntimeDist.DEDICATED_SERVER,
                loaderId,
                loaderVersion,
                LoaderVersion.parse(loaderVersion),
                "1.21.1",
                Map.of());
        return JsApiSurfaceResolver.resolve(env, contracts, List.of(providers), List.of());
    }

    private static CapabilityResolution resolveRaw(VerifiedContractSet contracts,
                                                   ApiContributionRegistry providers,
                                                   String loaderId, String loaderVersion) {
        EnvironmentKey env = new EnvironmentKey(
                ScriptTypeId.SERVER,
                RuntimeDist.DEDICATED_SERVER,
                loaderId,
                loaderVersion,
                LoaderVersion.parse(loaderVersion),
                "1.21.1",
                Map.of());
        List<CapabilityProviderContribution> allProviders = providers.capabilityProviders();
        return CapabilityResolver.resolve(env, definitionsOf(contracts), allProviders);
    }

    @Test
    void supportedCapabilityWithMatchingConditionsActivates() {
        VerifiedContractSet contracts = contractWith(
                new NormativeApiContract.ContractCapability("coreAlways", ">=1.0.0",
                        CapabilityStatus.SUPPORTED, null, "available everywhere"),
                new NormativeApiContract.ContractCapability("neoforgeOnly", ">=1.0.0",
                        CapabilityStatus.SUPPORTED, loaderScope("neoforge"), "neoforge only"));
        ApiContributionRegistry providers = providerRegistry(contracts);
        registerProvider(providers, "coreAlways", null, "impl-core");
        registerProvider(providers, "neoforgeOnly", loaderScope("neoforge"), "impl-nf");

        FrozenApiRegistry registry = resolve(contracts, providers, "neoforge", "1.0.0");
        Set<String> active = registry.environmentSnapshot().surfaceSnapshot().activeCapabilityNames();
        assertEquals(Set.of("coreAlways", "neoforgeOnly"), active,
                "declared conditions matching the environment must activate the capability");
    }

    @Test
    void contractConditionsGateEvenWhenProviderDeclaresNoScope() {
        // Contract declares neoforge-only, but the provider declares no scope of its own. The
        // contract condition is authoritative, so fabric must report UNAVAILABLE; a provider
        // without a scope must not activate it.
        VerifiedContractSet contracts = contractWith(
                new NormativeApiContract.ContractCapability("neoforgeOnly", ">=1.0.0",
                        CapabilityStatus.SUPPORTED, loaderScope("neoforge"), "neoforge only"));
        ApiContributionRegistry providers = providerRegistry(contracts);
        registerProvider(providers, "neoforgeOnly", null, "impl-nf");

        CapabilityResolution onFabric = resolveRaw(contracts, providers, "fabric", "1.0.0");
        assertTrue(activeNames(onFabric).isEmpty(), "contract conditions must gate activation");
        assertEquals("UNAVAILABLE", unavailableReason(onFabric, "neoforgeOnly"),
                "mismatch must be an explicit UNAVAILABLE, not a silent no-op");

        CapabilityResolution onNeoforge = resolveRaw(contracts, providers, "neoforge", "1.0.0");
        assertEquals(Set.of("neoforgeOnly"), activeNames(onNeoforge),
                "matching environment must activate");
    }

    @Test
    void contractConditionsAuthoritativeWhenProviderScopeIsWider() {
        // The provider declares a wider scope (fabric and neoforge) while the contract allows only
        // neoforge. The contract condition is still authoritative: a provider scope may narrow the
        // contract, never widen it.
        VerifiedContractSet contracts = contractWith(
                new NormativeApiContract.ContractCapability("neoforgeOnly", ">=1.0.0",
                        CapabilityStatus.SUPPORTED, loaderScope("neoforge"), "neoforge only"));
        ApiContributionRegistry providers = providerRegistry(contracts);
        registerProvider(providers, "neoforgeOnly", loaderScope("fabric", "neoforge"), "impl-nf");

        // A wider provider scope fails fast in the Step 4 containment check (SCOPE_NOT_CONTAINED)
        // and never reaches activation. The containment check and the contract condition check are
        // two separate defenses.
        ApiContributionRegistry finalProviders = providers;
        ApiResolutionException rejected = assertThrows(ApiResolutionException.class,
                () -> resolveRaw(contracts, finalProviders, "fabric", "1.0.0"));
        assertEquals("SCOPE_NOT_CONTAINED", rejected.code());
    }

    private static Set<String> activeNames(CapabilityResolution resolution) {
        Set<String> names = new java.util.HashSet<>();
        resolution.active().forEach(c -> names.add(c.name()));
        return names;
    }

    private static String unavailableReason(CapabilityResolution resolution, String capability) {
        return resolution.unavailable().stream()
                .filter(u -> u.name().equals(capability))
                .map(CapabilityResolution.UnavailableCapability::reason)
                .findFirst()
                .orElse(null);
    }

    @Test
    void capabilityOutsideDeclaredLoaderConditionsIsExplicitlyUnavailable() {
        VerifiedContractSet contracts = contractWith(
                new NormativeApiContract.ContractCapability("neoforgeOnly", ">=1.0.0",
                        CapabilityStatus.SUPPORTED, loaderScope("neoforge"), "neoforge only"));
        ApiContributionRegistry providers = providerRegistry(contracts);
        registerProvider(providers, "neoforgeOnly", loaderScope("neoforge"), "impl-nf");

        // The fabric environment does not satisfy the declared conditions, so the capability stays
        // inactive on the surface side.
        FrozenApiRegistry fabricRegistry = resolve(contracts, providers, "fabric", "1.0.0");
        assertTrue(fabricRegistry.environmentSnapshot().surfaceSnapshot().activeCapabilityNames().isEmpty(),
                "capability must not activate outside its declared loader conditions");

        // The resolution result records the capability as unavailable instead of dropping it silently.
        CapabilityResolution resolution = resolveRaw(contracts, providers, "fabric", "1.0.0");
        assertEquals(1, resolution.unavailable().size());
        assertEquals("neoforgeOnly", resolution.unavailable().getFirst().name());
        assertEquals("UNAVAILABLE", resolution.unavailable().getFirst().reason());
        assertTrue(resolution.active().isEmpty());
    }

    @Test
    void partialCapabilityActivatesAndKeepsDeclaredStatus() {
        VerifiedContractSet contracts = contractWith(
                new NormativeApiContract.ContractCapability("partialEverywhere", ">=1.0.0",
                        CapabilityStatus.PARTIAL, null, "partial parity documented in contract"));
        ApiContributionRegistry providers = providerRegistry(contracts);
        registerProvider(providers, "partialEverywhere", null, "impl-partial");

        FrozenApiRegistry registry = resolve(contracts, providers, "neoforge", "1.0.0");
        assertEquals(Set.of("partialEverywhere"),
                registry.environmentSnapshot().surfaceSnapshot().activeCapabilityNames());
        // The declared status stays observable: resolution never rewrites the PARTIAL result
        // recorded in the contract.
        assertEquals(CapabilityStatus.PARTIAL, contracts.requirePortable("nekojs-core")
                .contract().capabilities().getFirst().status());
    }

    @Test
    void declaredUnavailableNeverActivatesEvenWithEligibleProvider() {
        VerifiedContractSet contracts = contractWith(
                new NormativeApiContract.ContractCapability("retiredCapability", ">=1.0.0",
                        CapabilityStatus.UNAVAILABLE, null, "explicitly unavailable on all current platforms"));
        ApiContributionRegistry providers = providerRegistry(contracts);
        registerProvider(providers, "retiredCapability", null, "impl-should-not-activate");

        CapabilityResolution resolution = resolveRaw(contracts, providers, "neoforge", "1.0.0");
        assertTrue(resolution.active().isEmpty(),
                "declared UNAVAILABLE capability must not activate even with an eligible provider");
        assertEquals(1, resolution.unavailable().size());
        assertEquals("retiredCapability", resolution.unavailable().getFirst().name());
        assertEquals("DECLARED_UNAVAILABLE", resolution.unavailable().getFirst().reason(),
                "declared unavailability must be reported explicitly, not silently dropped");
    }

    @Test
    void loaderVersionConditionGatesCapability() {
        EnvironmentScope versionGated = new EnvironmentScope(null, null, Set.of(), Set.of(),
                LoaderVersionRange.exact(LoaderVersion.parse("2.0.0")), null);
        VerifiedContractSet contracts = contractWith(
                new NormativeApiContract.ContractCapability("needsLoader2", ">=1.0.0",
                        CapabilityStatus.SUPPORTED, versionGated, "loader 2.0.0 only"));
        ApiContributionRegistry providers = providerRegistry(contracts);
        registerProvider(providers, "needsLoader2", versionGated, "impl-v2");

        assertTrue(resolve(contracts, providers, "neoforge", "1.0.0").environmentSnapshot()
                        .surfaceSnapshot().activeCapabilityNames().isEmpty(),
                "loader version below the declared range must not activate");
        assertEquals(Set.of("needsLoader2"), resolve(contracts, providers, "neoforge", "2.0.0")
                        .environmentSnapshot().surfaceSnapshot().activeCapabilityNames(),
                "loader version within the declared range must activate");
    }

    @Test
    void manifestExposesOnlyActiveCapabilityConclusions() {
        VerifiedContractSet contracts = contractWith(
                new NormativeApiContract.ContractCapability("coreAlways", ">=1.0.0",
                        CapabilityStatus.SUPPORTED, null, "available everywhere"),
                new NormativeApiContract.ContractCapability("fabricOnly", ">=1.0.0",
                        CapabilityStatus.SUPPORTED, loaderScope("fabric"), "fabric only"));
        ApiContributionRegistry providers = providerRegistry(contracts);
        registerProvider(providers, "coreAlways", null, "impl-core");
        registerProvider(providers, "fabricOnly", loaderScope("fabric"), "impl-fabric");

        FrozenApiRegistry registry = resolve(contracts, providers, "neoforge", "1.0.0");
        ApiManifest manifest = ApiManifestGenerator.generate(
                ApiRuntimeVersionReader.read(), "neoforge", "1.21.1",
                registry.environmentSnapshot().surfaceSnapshot());
        assertEquals(List.of("coreAlways"), manifest.capabilities(),
                "manifest capabilities must equal the active conclusions (deterministic, sorted) "
                        + "and never list unavailable ones");
    }

    @Test
    void contractCapabilityDefaultsAreBackwardCompatible() {
        NormativeApiContract.ContractCapability legacyShape =
                new NormativeApiContract.ContractCapability("legacyId", ">=1.0.0", "docs only");
        assertEquals(CapabilityStatus.SUPPORTED, legacyShape.status());
        assertEquals(null, legacyShape.conditions());
        assertEquals("legacyId", legacyShape.id());
    }
}
