package com.tkisor.nekojs.core.ui;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.contract.VerifiedApiContract;
import com.tkisor.nekojs.api.surface.ApiEnvironmentSnapshot;
import com.tkisor.nekojs.api.surface.ApiManifest;
import com.tkisor.nekojs.api.surface.ApiSymbol;
import com.tkisor.nekojs.api.surface.ApiSymbolId;
import com.tkisor.nekojs.core.api.FrozenApiRegistry;
import com.tkisor.nekojs.core.api.JsApiSurfaceResolver;
import com.tkisor.nekojs.api.ui.Ui;
import com.tkisor.nekojs.api.ui.UiAlignment;
import com.tkisor.nekojs.api.ui.UiAnchor;
import com.tkisor.nekojs.api.ui.UiEventType;
import com.tkisor.nekojs.api.ui.UiPrimitive;
import com.tkisor.nekojs.api.ui.UiProp;
import com.tkisor.nekojs.api.ui.UiSpec;
import com.tkisor.nekojs.core.api.ApiManifestGenerator;
import com.tkisor.nekojs.core.api.ApiRuntimeVersionReader;
import com.tkisor.nekojs.core.api.CoreManagedApiBootstrap;
import com.tkisor.nekojs.probe.backend.typescript.ManagedApiDeclarationGenerator;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AC6：UI facade、primitive、props、事件对象、signal/store 和 capability 进入 09 的
 * {@code NormativeApiContract} 派生链；TypeScript JSX declaration 与 Probe 输出同源生成，
 * 不存在「声明有而 runtime 无」或「runtime 有而声明缺失」的能力。<br>
 * AC7：UI 交付物不创建第二份 catalog/声明事实源。
 */
class UiContractChainTest {

    private static final URI CODE_SOURCE = URI.create("file:///ticket40-ui-common-core.jar");

    @BeforeAll
    static void init() {
        TestPlatformInit.ensureInitialized();
    }

    @org.junit.jupiter.api.BeforeEach
    void clearBindingSchema() {
        com.tkisor.nekojs.api.event.ScriptBindingSchema.clearAll();
    }

    @Test
    void uiFacadeAndDataTypesAreInTheNormativeContract() {
        VerifiedApiContract contract = CoreManagedApiBootstrap.buildContract(CODE_SOURCE);
        Set<String> ids = contract.contract().symbols().stream()
                .map(symbol -> symbol.id().value())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        assertTrue(ids.contains("global:Ui"), "global:Ui must be a contract symbol: " + ids);
        assertTrue(ids.contains("member:Ui.create"), "member:Ui.create must be a contract symbol");
        assertTrue(ids.contains("member:Ui.open"), "member:Ui.open must be a contract symbol");
        assertTrue(ids.contains("member:Ui.createStateOwner"),
                "member:Ui.createStateOwner must be a contract symbol");
        assertTrue(ids.contains("member:Ui.closeGeneration"),
                "member:Ui.closeGeneration must be a contract symbol");
        assertTrue(ids.contains("member:UiProp.jsName"), "UiProp must be reflected as a receiver type");
        assertTrue(ids.contains("member:UiSignal.get"), "UiSignal must be reflected as a receiver type");
        assertTrue(ids.contains("member:UiStore.read"), "UiStore must be reflected as a receiver type");
        assertTrue(ids.contains("member:UiEvent.type"), "UiEvent must be reflected as a receiver type");
    }

    @Test
    void uiCapabilitiesAreDeclaredInTheContract() {
        var capabilities = CoreManagedApiBootstrap.buildContract(CODE_SOURCE)
                .contract().capabilities();
        Set<String> ids = capabilities.stream()
                .map(com.tkisor.nekojs.api.contract.NormativeApiContract.ContractCapability::id)
                .collect(java.util.stream.Collectors.toSet());
        assertEquals(new LinkedHashSet<>(UiSpec.CAPABILITY_IDS), new LinkedHashSet<>(Ui.capabilityIds()),
                "Ui.capabilityIds must be derived from the same constant source as the contract");
        assertTrue(ids.containsAll(UiSpec.CAPABILITY_IDS),
                "UI capability ids must be declared in the normative contract: " + ids);
    }

    @Test
    void frozenSurfaceAndManifestCarryExactlyTheUiContractSymbols() {
        CoreManagedApiBootstrap.CoreManagedApi core =
                CoreManagedApiBootstrap.load(new EmptyPlatform(), CODE_SOURCE);
        FrozenApiRegistry registry = JsApiSurfaceResolver.resolve(
                serverEnvironment(), core.contracts(),
                List.of(core.contributions()), List.of());

        Set<String> contractIds = core.contracts().requirePortable("nekojs-core")
                .contract().symbols().stream().map(s -> s.id().value())
                .collect(java.util.stream.Collectors.toSet());
        Set<String> surfaceIds = registry.environmentSnapshot().surfaceSnapshot().symbols()
                .stream().map(s -> s.id().value()).collect(java.util.stream.Collectors.toSet());
        assertEquals(contractIds, surfaceIds, "frozen surface must contain exactly the contract symbols");

        ApiManifest manifest = ApiManifestGenerator.generate(
                ApiRuntimeVersionReader.read(), "test", "0.0.0",
                registry.environmentSnapshot().surfaceSnapshot());
        Set<String> manifestIds = manifest.symbols().stream()
                .map(ApiManifest.ManifestSymbol::id).collect(java.util.stream.Collectors.toSet());
        assertEquals(contractIds, manifestIds, "manifest must cover exactly the contract symbols");
    }

    @Test
    void noRuntimeUiSymbolIsMissingFromTheContract() {
        CoreManagedApiBootstrap.CoreManagedApi core =
                CoreManagedApiBootstrap.load(new EmptyPlatform(), CODE_SOURCE);
        Set<String> contractIds = core.contracts().requirePortable("nekojs-core")
                .contract().symbols().stream().map(s -> s.id().value())
                .collect(java.util.stream.Collectors.toSet());

        List<ApiSymbolId> runtimeUiSymbols = new ArrayList<>();
        for (var contribution : core.contributions().symbolContributions()) {
            String id = contribution.symbolId().value();
            if (id.contains("Ui") || id.contains("ui")) {
                runtimeUiSymbols.add(contribution.symbolId());
            }
        }
        assertFalse(runtimeUiSymbols.isEmpty(), "UI runtime contributions must exist");
        for (ApiSymbolId symbolId : runtimeUiSymbols) {
            assertTrue(contractIds.contains(symbolId.value()),
                    "runtime UI symbol missing from the contract: " + symbolId.value());
        }
    }

    /**
     * 「声明有而 runtime 无」在 facade 成员上是零漂移：{@code global:Ui} 与
     * {@code member:Ui.*} 的每个契约符号都必须有运行时贡献。
     *
     * <p>数据类型的只读访问器（{@code member:UiProp.*}、{@code member:UiSignal.*} 等）
     * 与既有 {@code member:PerfTimer.label}、{@code member:PerfTimer.marks} 同一形态：
     * 由契约与声明承诺类型形状，但没有独立 handler（脚本不会拿到这些 Java 实例）。
     * 这条既有边界不属于本票引入，也不在 AC6 的 facade/capability 承诺范围内。
     */
    @Test
    void everyContractUiFacadeMemberHasARuntimeImplementation() {
        CoreManagedApiBootstrap.CoreManagedApi core =
                CoreManagedApiBootstrap.load(new EmptyPlatform(), CODE_SOURCE);
        Set<String> implemented = core.contributions().symbolContributions().stream()
                .map(contribution -> contribution.symbolId().value())
                .collect(java.util.stream.Collectors.toSet());
        Set<String> contractIds = core.contracts().requirePortable("nekojs-core")
                .contract().symbols().stream()
                .map(symbol -> symbol.id().value())
                .collect(java.util.stream.Collectors.toSet());

        assertTrue(contractIds.contains("global:Ui"));
        assertTrue(implemented.contains("global:Ui"), "global:Ui must have a runtime implementation");
        Set<String> facadeMembers = contractIds.stream()
                .filter(id -> id.startsWith("member:Ui."))
                .collect(java.util.stream.Collectors.toSet());
        assertFalse(facadeMembers.isEmpty(), "the Ui facade must expose script members");
        for (String id : facadeMembers) {
            assertTrue(implemented.contains(id),
                    "Ui facade contract member has no runtime implementation: " + id);
        }
    }

    @Test
    void managedApiDeclarationRendersUiFacadeAndDataTypes() {
        CoreManagedApiBootstrap.CoreManagedApi core =
                CoreManagedApiBootstrap.load(new EmptyPlatform(), CODE_SOURCE);
        FrozenApiRegistry registry = JsApiSurfaceResolver.resolve(
                serverEnvironment(), core.contracts(),
                List.of(core.contributions()), List.of());
        ApiEnvironmentSnapshot env = registry.environmentSnapshot();
        String ts = new ManagedApiDeclarationGenerator()
                .generate(Map.of(ScriptType.SERVER, env), ScriptType.SERVER);

        assertTrue(ts.contains("const Ui: $Ui"), "managed declaration must render the Ui facade: " + ts);
        assertTrue(ts.contains("interface $Ui "), ts);
        assertTrue(ts.contains("interface $UiProp"), ts);
        assertTrue(ts.contains("interface $UiSignal"), ts);
        assertTrue(ts.contains("interface $UiEvent"), ts);
    }

    @Test
    void jsxDeclarationIsGeneratedFromTheSameRegistryAsRuntime() {
        Map<String, List<String>> declared = UiJsxDeclarationGenerator.declaredPropNames();
        Map<String, List<String>> runtime = UiPrimitiveRegistry.snapshot();
        assertEquals(new LinkedHashSet<>(runtime.keySet()), new LinkedHashSet<>(declared.keySet()),
                "declared intrinsic elements must equal the registered primitive set");
        for (Map.Entry<String, List<String>> entry : runtime.entrySet()) {
            assertEquals(new LinkedHashSet<>(entry.getValue()),
                    new LinkedHashSet<>(declared.get(entry.getKey())),
                    "declared props for <" + entry.getKey() + "> must equal the registry");
        }
    }

    @Test
    void jsxDeclarationContainsNoRuntimeMissingCapabilityAndViceVersa() {
        String jsx = UiJsxDeclarationGenerator.generate();
        for (UiPrimitive primitive : UiPrimitive.values()) {
            assertTrue(jsx.contains("'" + primitive.tag() + "': {"),
                    "<" + primitive.tag() + "> must be declared as an intrinsic element");
        }
        for (UiProp prop : UiProp.values()) {
            boolean declaredSomewhere = UiJsxDeclarationGenerator.declaredPropNames().values().stream()
                    .anyMatch(names -> names.contains(prop.jsName()));
            assertTrue(declaredSomewhere, prop.jsName() + " must be declared on at least one primitive");
        }
        for (UiEventType type : UiEventType.values()) {
            assertTrue(jsx.contains("'" + type.jsName() + "'"),
                    "event type " + type.jsName() + " must be declared");
        }
        for (UiAlignment alignment : UiAlignment.values()) {
            assertTrue(jsx.contains("'" + alignment.jsName() + "'"),
                    "alignment " + alignment.jsName() + " must be declared");
        }
        for (UiAnchor anchor : UiAnchor.values()) {
            assertTrue(jsx.contains("'" + anchor.jsName() + "'"),
                    "anchor " + anchor.jsName() + " must be declared");
        }
    }

    @Test
    void jsxDeclarationIsDeterministicAndHasNoHandWrittenDuplicate() {
        assertEquals(UiJsxDeclarationGenerator.generate(), UiJsxDeclarationGenerator.generate(),
                "generator must be deterministic");
        String jsx = UiJsxDeclarationGenerator.generate();
        assertTrue(jsx.contains("interface IntrinsicElements extends NekoJsx.UiPrimitiveProps"),
                "JSX.IntrinsicElements must be derived from the registry-backed props interface");
        assertTrue(jsx.contains("const Ui: NekoJsx.UiFacade;"),
                "the facade global must be declared for script authors");
        assertTrue(UiJsxDeclarationGenerator.generateRuntimeModule()
                        .contains("declare module 'nekojs/jsx-runtime'"),
                "the automatic runtime module declaration must be generated from the same source");
    }

    @Test
    void uiPrimitivesAndPropNamesAreExposedAsDerivedViews() {
        List<String> primitives = Ui.primitives();
        assertEquals(UiPrimitiveRegistry.tags(), primitives);
        assertEquals(UiProp.values().length, Ui.propNames().size());
        assertEquals(UiEventType.values().length, Ui.eventTypeNames().size());
        // 派生视图：每次调用返回新的不可变列表，不构成 UI 专用 global singleton 状态
        assertNotNull(Ui.propNames());
        assertEquals(11, primitives.size());
    }

    private static com.tkisor.nekojs.api.surface.EnvironmentKey serverEnvironment() {
        return new com.tkisor.nekojs.api.surface.EnvironmentKey(
                com.tkisor.nekojs.api.surface.ScriptTypeId.SERVER,
                com.tkisor.nekojs.api.surface.RuntimeDist.DEDICATED_SERVER,
                "neoforge",
                "21.1.0",
                com.tkisor.nekojs.api.surface.LoaderVersion.parse("21.1.0"),
                "1.21.1",
                Map.of());
    }

    /** 与 {@code CoreManagedApiBootstrapTest} 一致的最小测试环境。 */
    private static final class EmptyPlatform implements com.tkisor.nekojs.platform.IPlatform {
        @Override public boolean isClient() { return false; }
        @Override public boolean isDevelopment() { return false; }
        @Override public String getMcVersion() { return "1.21.1"; }
        @Override public String getLoaderId() { return "neoforge"; }
        @Override public String getLoaderVersion() { return "21.1.0"; }
        @Override public java.nio.file.Path getGameDir() { return java.nio.file.Path.of("."); }
        @Override public Map<String, com.tkisor.nekojs.platform.IModInfo> getMods() { return Map.of(); }
        @Override public com.tkisor.nekojs.platform.IModInfo getInfo(String modID) { return null; }
    }
}