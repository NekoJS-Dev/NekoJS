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
 * AC3（ticket 09）legacy 观察面 characterization：
 * <ul>
 *   <li>legacy catalog 符号可被 {@link LegacySurfaceAdapter} 转换为迁移观察面
 *       （{@code NekoScriptCatalogSnapshot.legacySurface()}），但<b>不进入 managed surface</b>、
 *       不获得 managed stable 身份；</li>
 *   <li>同名冲突时 managed 契约身份原样保留（不被覆盖、不合并签名），resolver 层把
 *       legacy 保留名对 managed global 的遮蔽判为 {@code LEGACY_NAME_COLLISION} 显式失败。</li>
 * </ul>
 * 只固化既有行为（characterization），不改变迁移观察职责。
 */
class LegacySurfaceShadowCharacterizationTest {

    private static final URI TEST_CODE_SOURCE = URI.create("file:///test-nekojs-legacy-shadow.jar");

    /** 与 managed global 同名的 legacy binding（观察面必收，但不得覆盖 managed 契约）。 */
    private static final String MANAGED_NAME = "Text";

    @Test
    void legacySurfaceKeepsObservationEntryForManagedNamedSymbol() {
        BindingCatalogEntry legacyBinding = BindingCatalogEntry.of(
                MANAGED_NAME, ScriptType.SERVER, LegacyApiStub.class, true);
        List<ApiSymbol> legacy = LegacySurfaceAdapter.fromBindings(List.of(legacyBinding));

        assertEquals(1, legacy.size());
        assertEquals(ApiSymbolId.parse("global:" + MANAGED_NAME), legacy.getFirst().id(),
                "legacy binding maps to an observation id in the legacy surface");
        // 观察面占位签名：无参 function（kind=global 仅为迁移观察元数据，不是 stable 承诺）
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
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(),
                new TypeOutputLayout(Path.of("probe-types"), Path.of("snippets")),
                Map.of(ScriptType.SERVER, managed),
                legacySurface);

        // managed surface 原样保留：契约符号一个不少
        Set<String> contractIds = contractSymbolIds();
        Set<String> managedIds = snapshot.managedApis().get(ScriptType.SERVER).surfaceSnapshot()
                .symbols().stream().map(s -> s.id().value()).collect(Collectors.toSet());
        assertEquals(contractIds, managedIds,
                "legacy catalog entries must not add, remove or replace managed surface symbols");

        // 同名 global:Text 在 managed surface 中仍是契约签名（object 全局对象），未被 legacy void 签名覆盖
        ApiSymbol managedText = symbolById(
                snapshot.managedApis().get(ScriptType.SERVER).surfaceSnapshot().symbols(), "global:" + MANAGED_NAME);
        assertNotNull(managedText);
        ApiSignature managedSig = managedText.signatures().getFirst();
        assertNotEquals(ApiSignature.function(List.of(), com.tkisor.nekojs.api.surface.ApiTypeRef.voidType()).callKey()
                        + ":" + com.tkisor.nekojs.api.surface.ApiTypeRef.voidType().compatibilityKey(),
                managedSig.callKey() + ":" + managedSig.returnType().compatibilityKey(),
                "managed global must not be shadowed by the legacy placeholder signature");

        // legacy 面独立存在：观察条目在 legacySurface，而非 managedApis
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
        // 重新 build 一份 contributions（load 的 contributions 已绑定同一 contracts，可直接用）
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
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                new TypeOutputLayout(Path.of("probe-types"), Path.of("snippets")),
                Map.of(), List.of());
        List<ApiSymbol> legacy = LegacySurfaceAdapter.convert(legacyOnly);

        Set<String> kinds = legacy.stream().map(s -> s.id().kind()).collect(Collectors.toSet());
        assertEquals(Set.of("global", "event", "adapter"), kinds,
                "legacy observation ids use observation kinds, not managed stable kinds");

        for (ApiSymbol symbol : legacy) {
            // 观察面占位签名：无参、void 返回——不承载 managed 契约承诺
            assertEquals(1, symbol.signatures().size());
            assertEquals(List.of(), symbol.signatures().getFirst().parameters());
        }
        // LEGACY_PREVIEW tier 仍是分层词汇的一部分（保留给贡献层使用），managed surface 不消费它
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

    /** legacy 观察面用例的最小宿主类。 */
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
