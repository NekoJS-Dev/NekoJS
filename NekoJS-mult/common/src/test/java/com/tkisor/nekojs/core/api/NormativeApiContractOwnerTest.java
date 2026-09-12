package com.tkisor.nekojs.core.api;

import com.tkisor.nekojs.api.contract.ApiContractIdentity;
import com.tkisor.nekojs.core.api.ApiSurfaceTestSupport;
import com.tkisor.nekojs.api.contract.ApiContractKind;
import com.tkisor.nekojs.api.contract.NormativeApiContract;
import com.tkisor.nekojs.api.contract.VerifiedApiContract;
import com.tkisor.nekojs.api.data.NbtEntry;
import com.tkisor.nekojs.api.data.NekoId;
import com.tkisor.nekojs.api.data.PerfTimerValue;
import com.tkisor.nekojs.api.data.TextValue;
import com.tkisor.nekojs.api.facade.IdFacade;
import com.tkisor.nekojs.api.facade.JsonFacade;
import com.tkisor.nekojs.api.facade.ModInfoValue;
import com.tkisor.nekojs.api.facade.NbtFacade;
import com.tkisor.nekojs.api.facade.PerformanceFacade;
import com.tkisor.nekojs.api.facade.PlatformFacade;
import com.tkisor.nekojs.api.facade.RegistryFacade;
import com.tkisor.nekojs.api.facade.RegistryView;
import com.tkisor.nekojs.api.facade.TextFacade;
import com.tkisor.nekojs.api.surface.ApiManifest;
import com.tkisor.nekojs.api.surface.ApiRuntimeVersions;
import com.tkisor.nekojs.api.surface.ApiSymbol;
import com.tkisor.nekojs.api.surface.ApiSymbolId;
import com.tkisor.nekojs.api.surface.EnvironmentKey;
import com.tkisor.nekojs.api.surface.LoaderVersion;
import com.tkisor.nekojs.api.surface.RuntimeDist;
import com.tkisor.nekojs.api.surface.ScriptTypeId;
import com.tkisor.nekojs.platform.IModInfo;
import com.tkisor.nekojs.platform.IPlatform;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AC1（ticket 09）最高调用者测试：facade、数据类型与事件注册类是
 * {@link NormativeApiContract}（经 {@link CoreManagedApiBootstrap}）的<b>唯一</b>反射输入。
 *
 * <p>测试只穿过最高调用者 Seam（{@code CoreManagedApiBootstrap.load}/
 * {@code buildContract} → {@code JsApiSurfaceResolver} → {@code ApiManifestGenerator}），
 * 不测试反射器私有内部状态。核心手段是<b>双记账</b>：测试侧独立重放与
 * {@code buildContract} 完全相同的反射调用序列，再断言契约符号列表与之逐元素相等——
 * 任何绕过反射输入（手写符号、第二 JSON、隐式补充清单）进入契约都会让本测试变红。
 */
class NormativeApiContractOwnerTest {

    private static final URI TEST_CODE_SOURCE = URI.create("file:///test-nekojs-owner.jar");

    /**
     * 测试侧维护的规范输入清单（与 {@code CoreManagedApiBootstrap.buildContract} 的
     * 反射序列一一对应）。该清单是「契约输入集合恰好等于反射集合」断言的独立副本：
     * 两侧任何一侧未同步（新增/删除 facade、数据类型或事件注册类）测试立即失败，
     * 迫使变更显式经过本文件审阅。
     */
    private static List<ApiSymbol> reflectExpectedInputs() {
        List<ApiSymbol> symbols = new ArrayList<>();
        symbols.addAll(ContractReflector.extractSymbols("ID", IdFacade.class));
        symbols.addAll(ContractReflector.extractSymbols("Platform", PlatformFacade.class));
        symbols.addAll(ContractReflector.extractSymbols("Text", TextFacade.class));
        symbols.addAll(ContractReflector.extractSymbols("JsonIO", JsonFacade.class));
        symbols.addAll(ContractReflector.extractSymbols("NBT", NbtFacade.class));
        symbols.addAll(ContractReflector.extractSymbols("Registry", RegistryFacade.class));
        symbols.addAll(ContractReflector.extractSymbols("Performance", PerformanceFacade.class));
        symbols.addAll(ContractReflector.reflectDataType("TextValue", TextValue.class));
        symbols.addAll(ContractReflector.reflectDataType("RegistryView", RegistryView.class));
        symbols.addAll(ContractReflector.reflectDataType("NbtEntry", NbtEntry.class));
        symbols.addAll(ContractReflector.reflectDataType("ModInfo", ModInfoValue.class));
        symbols.addAll(ContractReflector.reflectDataType("PerfTimer", PerfTimerValue.class));
        symbols.addAll(ContractReflector.reflectEventRegistrationSymbols());
        return symbols;
    }

    @Test
    void contractSymbolsEqualExactReflectionInputUnion() {
        NormativeApiContract contract = CoreManagedApiBootstrap.buildContract(TEST_CODE_SOURCE).contract();
        List<ApiSymbol> expected = reflectExpectedInputs();

        assertIterableEquals(expected, contract.symbols(),
                "NormativeApiContract symbols must be exactly the reflected facade/data-type/event "
                        + "inputs, in order; a mismatch means a second (non-reflective) input leaked "
                        + "into the contract owner");
    }

    @Test
    void contractIdentityIsTheBootstrapOwnedPortableCore() {
        VerifiedApiContract contract = CoreManagedApiBootstrap.buildContract(TEST_CODE_SOURCE);
        ApiContractIdentity identity = contract.identity();
        assertEquals("nekojs-core", identity.owner());
        assertEquals(ApiContractKind.PORTABLE, identity.kind());
        assertEquals("portable-core", identity.contractId());
        assertEquals("nekojs/api-contract/synthesized", contract.resourceName(),
                "contract must be synthesized from reflection, not loaded from a hand-written JSON");
        assertEquals(contract.integritySha256(), contract.compatibilitySha256(),
                "synthesized contract pins integrity and compatibility to the same reflected content hash");
    }

    @Test
    void highestCallerLoadCarriesTheSameReflectedContract() {
        CoreManagedApiBootstrap.CoreManagedApi core = CoreManagedApiBootstrap.load(
                new EmptyPlatform(), TEST_CODE_SOURCE);
        VerifiedApiContract viaLoad = core.contracts().requirePortable("nekojs-core");
        VerifiedApiContract direct = CoreManagedApiBootstrap.buildContract(TEST_CODE_SOURCE);

        assertEquals(direct.identity(), viaLoad.identity());
        assertEquals(direct.contract().symbols(), viaLoad.contract().symbols(),
                "load() must not add, drop or rewrite contract symbols relative to buildContract()");
        assertEquals(direct.integritySha256(), viaLoad.integritySha256());
    }

    @Test
    void resolvedFrozenSurfaceContainsExactlyContractSymbols() {
        CoreManagedApiBootstrap.CoreManagedApi core = CoreManagedApiBootstrap.load(
                new EmptyPlatform(), TEST_CODE_SOURCE);
        FrozenApiRegistry registry = JsApiSurfaceResolver.resolve(
                ApiSurfaceTestSupport.serverEnvironment(), core.contracts(), List.of(core.contributions()), List.of());

        Set<String> contractIds = core.contracts().requirePortable("nekojs-core")
                .contract().symbols().stream().map(s -> s.id().value()).collect(java.util.stream.Collectors.toSet());
        Set<String> surfaceIds = registry.environmentSnapshot().surfaceSnapshot().symbols()
                .stream().map(s -> s.id().value()).collect(java.util.stream.Collectors.toSet());

        assertEquals(contractIds, surfaceIds,
                "frozen surface (the input of manifest/Probe/TS declaration) must contain exactly "
                        + "the contract symbols: derivation input == reflection output");
    }

    @Test
    void manifestDerivationConsumesOnlyTheFrozenSurface() {
        CoreManagedApiBootstrap.CoreManagedApi core = CoreManagedApiBootstrap.load(
                new EmptyPlatform(), TEST_CODE_SOURCE);
        FrozenApiRegistry registry = JsApiSurfaceResolver.resolve(
                ApiSurfaceTestSupport.serverEnvironment(), core.contracts(), List.of(core.contributions()), List.of());

        ApiRuntimeVersions versions = ApiRuntimeVersionReader.read();
        ApiManifest manifest = ApiManifestGenerator.generate(
                versions, "test", "0.0.0", registry.environmentSnapshot().surfaceSnapshot());

        Set<String> contractIds = core.contracts().requirePortable("nekojs-core")
                .contract().symbols().stream().map(s -> s.id().value()).collect(java.util.stream.Collectors.toSet());
        List<String> manifestIds = manifest.symbols().stream().map(ApiManifest.ManifestSymbol::id).toList();

        assertEquals(contractIds, new HashSet<>(manifestIds),
                "manifest must cover exactly the contract symbols");
        assertEquals(manifestIds.size(), new HashSet<>(manifestIds).size(),
                "manifest symbols must not duplicate");
    }

    @Test
    void everyRegisteredContributionReferencesAContractSymbol() {
        CoreManagedApiBootstrap.CoreManagedApi core = CoreManagedApiBootstrap.load(
                new EmptyPlatform(), TEST_CODE_SOURCE);
        Set<String> contractIds = core.contracts().requirePortable("nekojs-core")
                .contract().symbols().stream().map(s -> s.id().value()).collect(java.util.stream.Collectors.toSet());

        List<com.tkisor.nekojs.api.surface.ApiContribution> contributions =
                core.contributions().symbolContributions();
        assertTrue(contributions.size() >= 100, "bootstrap should register the core surface");
        for (com.tkisor.nekojs.api.surface.ApiContribution contribution : contributions) {
            ApiSymbolId id = contribution.symbolId();
            assertTrue(contractIds.contains(id.value()),
                    "contribution references symbol outside the normative contract: " + id.value());
        }
    }

    /** 与 {@code CoreManagedApiBootstrapTest} 一致的最小测试环境。 */

    private static final class EmptyPlatform implements IPlatform {
        @Override public boolean isClient() { return false; }
        @Override public boolean isDevelopment() { return true; }
        @Override public String getMcVersion() { return "1.21.1"; }
        @Override public Path getGameDir() { return Path.of("."); }
        @Override public Map<String, IModInfo> getMods() { return Map.of(); }
        @Override public IModInfo getInfo(String modID) { return null; }
        @Override public String getLoaderId() { return "test"; }
        @Override public String getLoaderVersion() { return "0.0.0"; }
    }
}
