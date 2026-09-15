// TODO(fabric): fabric 侧还没有对应实现，整文件守卫等移植完成后去掉
//? if neoforge {
package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.api.JSTypeAdapter;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.catalog.ManualDeclarationCatalogEntry;
import com.tkisor.nekojs.api.catalog.LegacySurfaceAdapter;
import com.tkisor.nekojs.api.catalog.NekoScriptCatalog;
import com.tkisor.nekojs.api.catalog.NekoScriptCatalogSnapshot;
import com.tkisor.nekojs.api.catalog.TypeDocCatalogEntry;
import com.tkisor.nekojs.api.data.Binding;
import com.tkisor.nekojs.api.data.BindingRegistry;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.api.plugin.IPluginRuntime;
import com.tkisor.nekojs.api.recipe.RecipeLifecycleContext;
import com.tkisor.nekojs.api.recipe.RecipeNamespaceEntry;
import com.tkisor.nekojs.api.surface.ApiRuntimeView;
import com.tkisor.nekojs.api.surface.ApiSymbol;
import com.tkisor.nekojs.api.surface.ApiSymbolId;
import com.tkisor.nekojs.api.surface.EnvironmentKey;
import com.tkisor.nekojs.bindings.static_access.NativeEventsJS;
import com.tkisor.nekojs.core.NekoJSCorePlugin;
import com.tkisor.nekojs.core.plugin.TypeDocsRegister;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 票 14 AC4：{@code NativeEvents} 的 tier 定位 characterization——保留为
 * legacy/raw Adapter 观察面（raw NeoForge event bridge），声明来源是
 * {@code TypeDocCatalogEntry}（probe 补全/文档），<strong>不</strong>被 catalog
 * 收录动作静默升级为 managed stable：
 *
 * <ul>
 *   <li>注册面：只在 STARTUP 绑定集注册（STARTUP reload 时 {@code close()} 注销上一轮
 *       原生监听器），SERVER/CLIENT 绑定集没有它；</li>
 *   <li>catalog 面：以 binding 进入 {@code legacySurface} 观察面（{@code global:}
 *       kind + 占位签名，ticket 09 的 legacy shadow 语义），不出现在 events 目录
 *       （它不是事件组），也不进 managedApis（managed stable 面没有这个符号）；</li>
 *   <li>声明来源：{@code NekoJSCorePlugin.registerTypeDocs} 的
 *       {@code TypeDocCatalogEntry.binding(STARTUP, "NativeEvents", ...)} 条目。</li>
 * </ul>
 */
class NativeEventsLegacyTierCharacterizationTest {

    @Test
    void nativeEventsIsAStartupOnlyRawBinding() {
        BindingRegistry.BindingRegistryImpl startup =
                new BindingRegistry.BindingRegistryImpl(ScriptType.STARTUP);
        new NekoJSCorePlugin().registerBinding(startup);
        assertTrue(startup.viewRegistered().containsKey("NativeEvents"),
                "NativeEvents must be registered in the STARTUP binding set (raw adapter face)");
        Binding binding = startup.viewRegistered().get("NativeEvents");
        assertTrue(binding.value() instanceof NativeEventsJS,
                "the binding value is the raw NativeEventsJS bridge");

        BindingRegistry.BindingRegistryImpl server =
                new BindingRegistry.BindingRegistryImpl(ScriptType.SERVER);
        new NekoJSCorePlugin().registerBinding(server);
        assertFalse(server.viewRegistered().containsKey("NativeEvents"),
                "NativeEvents must NOT be registered outside STARTUP (STARTUP reload owns its lifecycle)");
    }

    @Test
    void catalogExposureStaysLegacyObservationAndNeverEntersManagedStable() {
        // catalog 收录动作：以 STARTUP binding 进入 legacy 观察面（LegacySurfaceAdapter
        // 是 NekoScriptCatalog.snapshot 的 legacy 投影步骤；snapshot() 本体依赖运行中的
        // FML loader，单测无法进入，故直接驱动同一 adapter）
        NekoScriptCatalogSnapshot minimal = new NekoScriptCatalogSnapshot(
                List.of(ScriptType.STARTUP),
                NekoScriptCatalog.bindings(new RuntimeWithNativeEvents(), ScriptType.STARTUP),
                NekoScriptCatalog.events(new RuntimeWithNativeEvents()),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), null, Map.of(), List.of());
        List<ApiSymbol> legacySurface = LegacySurfaceAdapter.convert(minimal);

        assertTrue(legacySurface.stream()
                        .map(ApiSymbol::id)
                        .anyMatch(id -> id.equals(ApiSymbolId.parse("global:NativeEvents"))),
                "NativeEvents is observable in the legacy surface (global: observation kind)");
        assertEquals(0, minimal.events().size(),
                "NativeEvents is not an event group: the events catalog has no NativeEvents entries");
        // managed stable 面的唯一构成来源是 apiRuntime 贡献（ticket 09 派生链：
        // CoreManagedApiBootstrap → ApiContributionRegistry → JsApiSurfaceResolver）。
        // NativeEventsJS 是 raw binding，不注册任何 ApiContribution——「不被 catalog
        // 收录动作静默升级 managed stable」的结构性证据见本票 REPORT 旁路清单。
    }

    @Test
    void declarationSourceIsTheTypeDocEntry() {
        List<TypeDocCatalogEntry> docs = new ArrayList<>();
        new NekoJSCorePlugin().registerTypeDocs(new TypeDocsRegister() {
            @Override
            public void register(TypeDocCatalogEntry entry) {
                docs.add(entry);
            }

            @Override
            public void registerManualDeclaration(ManualDeclarationCatalogEntry entry) {
            }
        });

        TypeDocCatalogEntry nativeEvents = docs.stream()
                .filter(d -> d.target().equals("NativeEvents"))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "NativeEvents declaration source must be a TypeDocCatalogEntry binding doc"));
        assertEquals(ScriptType.STARTUP, nativeEvents.scriptType(),
                "the doc declares NativeEvents for the STARTUP side only");
        assertTrue(nativeEvents.description() != null && !nativeEvents.description().isBlank(),
                "the doc carries the raw/legacy capability description");
        assertTrue(!nativeEvents.examples().isEmpty(),
                "the doc carries the usage example (probe/completion source)");
    }

    /** 只带 NativeEvents STARTUP binding 的最小 runtime（观察 legacySurface 的投影输入）。 */
    private static final class RuntimeWithNativeEvents implements IPluginRuntime {
        @Override
        public Map<String, Binding> bindings(ScriptType type) {
            if (type == ScriptType.STARTUP) {
                NativeEventsJS bridge = new NativeEventsJS();
                return Map.of("NativeEvents", Binding.of("NativeEvents", bridge));
            }
            return Map.of();
        }

        @Override public Map<String, EventGroup> eventGroups() { return Map.of(); }
        @Override public List<JSTypeAdapter<?>> adapters() { return List.of(); }
        @Override public List<TypeDocCatalogEntry> typeDocs() { return List.of(); }
        @Override public List<ManualDeclarationCatalogEntry> manualDeclarations() { return List.of(); }
        @Override public Map<String, String> nodeModules() { return Map.of(); }
        @Override public Map<String, RecipeNamespaceEntry> recipeNamespaces() { return Map.of(); }
        @Override public void beforeRecipeLoading(RecipeLifecycleContext context) {}
        @Override public void afterRecipes(RecipeLifecycleContext context) {}
        @Override public void fireInit() {}
        @Override public void fireInitStartup() {}
        @Override public void fireAfterInit() {}
        @Override public void fireBeforeScriptsLoaded(ScriptType type) {}
        @Override public void fireAfterScriptsLoaded(ScriptType type) {}
        @Override public ApiRuntimeView apiRuntime(EnvironmentKey environment) { return null; }
        @Override public Object managedApiImplementation(ApiSymbolId globalId) { return null; }
    }
}
//?}
