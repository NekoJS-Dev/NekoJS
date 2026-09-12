package com.tkisor.nekojs.core.api;

import com.tkisor.nekojs.api.capability.CapabilityDefinition;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AC4（ticket 09）：契约能力（supported/partial/unavailable）带 loader/版本/运行上下文
 * 条件，条件在 surface 解析时真实生效；不可用能力显式记录（DECLARED_UNAVAILABLE /
 * UNAVAILABLE），不静默 no-op；manifest 只暴露 active 能力结论（派生一致）。
 *
 * <p>全部断言穿过公开 Seam（{@code JsApiSurfaceResolver.resolve} → surface snapshot →
 * {@code ApiManifestGenerator}；resolver 直调仅用于核验 unavailable 显式记录），不测试私有状态。
 */
class ManagedSurfaceCapabilityConditionsTest {

    private static final URI CODE_SOURCE = URI.create("file:///test-capability.jar");
    private static final ApiVersion CONTRACT_VERSION = ApiVersion.parse("1.0.0");

    private static EnvironmentScope loaderScope(String... loaderIds) {
        return new EnvironmentScope(null, null, Set.of(), Set.of(loaderIds), null, null);
    }

    /** 单能力契约：owner 固定 nekojs-core/PORTABLE，便于复用 requirePortable。 */
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

    /** 契约能力 → resolver 定义的映射（与 JsApiSurfaceResolver.collectCapabilityDefinitions 一致）。 */
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

    /** provider 注册表：owner 必须与契约 owner 一致（nekojs-core），CORE_ONLY 策略接受核内 owner。 */
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
    void capabilityOutsideDeclaredLoaderConditionsIsExplicitlyUnavailable() {
        VerifiedContractSet contracts = contractWith(
                new NormativeApiContract.ContractCapability("neoforgeOnly", ">=1.0.0",
                        CapabilityStatus.SUPPORTED, loaderScope("neoforge"), "neoforge only"));
        ApiContributionRegistry providers = providerRegistry(contracts);
        registerProvider(providers, "neoforgeOnly", loaderScope("neoforge"), "impl-nf");

        // fabric 环境不满足声明条件：能力不激活（surface 侧）
        FrozenApiRegistry fabricRegistry = resolve(contracts, providers, "fabric", "1.0.0");
        assertTrue(fabricRegistry.environmentSnapshot().surfaceSnapshot().activeCapabilityNames().isEmpty(),
                "capability must not activate outside its declared loader conditions");

        // 解析结果侧显式记录 unavailable（非静默缺失）
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
        // 声明状态保持可观察：契约侧 PARTIAL 结论不被解析过程改写
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
