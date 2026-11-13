package com.tkisor.nekojs.core.dynamic.facade;

import com.tkisor.nekojs.api.JSTypeAdapter;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.catalog.EventCatalogEntry;
import com.tkisor.nekojs.api.catalog.ManualDeclarationCatalogEntry;
import com.tkisor.nekojs.api.catalog.NekoScriptCatalog;
import com.tkisor.nekojs.api.catalog.RegistryBuilderSurfaceEntry;
import com.tkisor.nekojs.api.catalog.TypeDocCatalogEntry;
import com.tkisor.nekojs.api.data.Binding;
import com.tkisor.nekojs.api.event.EventGroupRegistry;
import com.tkisor.nekojs.api.plugin.IPluginRuntime;
import com.tkisor.nekojs.api.recipe.RecipeLifecycleContext;
import com.tkisor.nekojs.api.recipe.RecipeNamespaceEntry;
import com.tkisor.nekojs.api.surface.ApiRuntimeView;
import com.tkisor.nekojs.api.surface.ApiSymbolId;
import com.tkisor.nekojs.api.surface.EnvironmentKey;
import com.tkisor.nekojs.core.dynamic.plan.DynamicDefinition;
import com.tkisor.nekojs.core.dynamic.plan.DynamicDefinitionType;
import com.tkisor.nekojs.core.dynamic.plan.DynamicBuilderSurfaces;
import com.tkisor.nekojs.probe.backend.python.ApiTypeRefPyRenderer;
import com.tkisor.nekojs.probe.backend.python.PythonEventRenderer;
import com.tkisor.nekojs.probe.backend.python.RegistryBuilderPyRenderer;
import com.tkisor.nekojs.probe.backend.typescript.AdapterAliasGenerator;
import com.tkisor.nekojs.probe.backend.typescript.EventDeclarationGenerator;
import com.tkisor.nekojs.probe.backend.typescript.RegistryBuilderTsRenderer;
import com.tkisor.nekojs.probe.types.TypeAliasRegistry;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fixture for the evidence chain: the caller interface (facade group and payload), the contract (builder
 * contract reflection), the TS and Python declarations (rendered by the production probe renderer), and the
 * local behavior tests all derive from <b>the same contract input</b>. Event entries enter the catalog (one
 * per group, SERVER side), builder entries share their source with runtime members and fingerprints, and the
 * TS and Python members mean the same thing.
 *
 * <p>TS/Python parity is checked with in-memory assertions; Python output is not part of a golden.
 * {@code DynamicRegistryEventsDeclarationGoldenTest} freezes the declaration shape as a contract/golden: two
 * goldens, the facade event declaration and the Builder declaration of the three frozen types, rendered by
 * the production probe renderer. The golden freezes only the declaration shape; the capability conclusions
 * live in the baseline REPORT table, and public activation is still decided by the transactional and
 * synchronous gate.
 */
class DynamicRegistryDeclarationParityTest {

    @BeforeAll
    static void initPlatform() {
        TestPlatformInit.ensureInitialized();
    }

    private static List<EventCatalogEntry> catalogServerEntries() {
        EventGroupRegistry registry = new EventGroupRegistry.Impl();
        registry.register(DynamicRegistryEvents.GROUP);
        return NekoScriptCatalog.events(new StubRuntime(registry.view()), ScriptType.SERVER);
    }

    @Test
    void facadeEntersTheCatalogAsOneServerEvent() {
        List<EventCatalogEntry> events = catalogServerEntries();
        assertEquals(1, events.size(), "一组一条总线一条目录");
        EventCatalogEntry entry = events.get(0);
        assertEquals("DynamicRegistryEvents", entry.group());
        assertEquals("dynamicRegistry", entry.name());
        assertEquals(ScriptType.SERVER, entry.scriptType());
        assertFalse(entry.scriptDefined(), "payload 是真实事件类（DynamicRegistryEventJS）");
        assertFalse(entry.dispatchable(), "普通总线（不分发 key）");

        EventGroupRegistry registry = new EventGroupRegistry.Impl();
        registry.register(DynamicRegistryEvents.GROUP);
        assertTrue(NekoScriptCatalog.events(new StubRuntime(registry.view()), ScriptType.CLIENT).isEmpty(),
                "CLIENT 侧过滤零条目");
    }

    @Test
    void typeScriptAndPythonDeclarationsRenderTheEventOnce() {
        List<EventCatalogEntry> entries = catalogServerEntries();
        TypeAliasRegistry aliases = new TypeAliasRegistry();
        String ts = new EventDeclarationGenerator(aliases, new AdapterAliasGenerator(aliases))
                .generate(entries, ScriptType.SERVER);
        assertEquals(1, count("function dynamicRegistry(", ts), "TS 声明恰好一次");

        String py = new PythonEventRenderer(new ApiTypeRefPyRenderer(Set.of()), Set.of())
                .render(ScriptType.SERVER, entries, null);
        assertEquals(1, count("def dynamicRegistry(", py), "Python 声明恰好一次");
        assertTrue(py.contains("DynamicRegistryEvents") || py.contains("dynamicRegistry"),
                "Python 组/成员名与 TS 同源");
    }

    @Test
    void builderSurfaceEntriesDeriveFromTheSameContractAsRuntimeMembers() {
        List<RegistryBuilderSurfaceEntry> entries = DynamicBuilderSurfaces.derive();
        assertEquals(3, entries.size(), "三个冻结类型各一条 builder 条目");
        assertEquals(List.of("DynamicItemBuilder", "DynamicMobEffectBuilder", "DynamicSoundEventBuilder"),
                entries.stream().map(RegistryBuilderSurfaceEntry::builderName).toList(),
                "字典序确定");
        for (RegistryBuilderSurfaceEntry entry : entries) {
            assertTrue(DynamicDefinition.supportedRegistryKeys().contains(entry.registryKey()),
                    entry.builderName() + " 目标注册表在冻结范围内: " + entry.registryKey());
            assertEquals("dynamic", entry.typeName());
            assertTrue(entry.members().stream().anyMatch(
                    member -> member.kind() == RegistryBuilderSurfaceEntry.MemberKind.WRITABLE_PROPERTY),
                    entry.builderName() + " 至少有一个可写属性（双形态 setter）");
            // sugarName is the name of the direct event entry point: event.item, event.soundEvent, event.mobEffect.
            assertTrue(DynamicDefinitionType.byApiName(entry.sugarName()).isPresent(),
                    "sugarName 是冻结的类型直达入口名: " + entry.sugarName());
        }
        // The entry members come from the same source as the runtime contract, so the name sets match.
        RegistryBuilderSurfaceEntry item = entries.get(0);
        Set<String> declared = Set.copyOf(item.members().stream().map(RegistryBuilderSurfaceEntry.Member::name)
                .toList());
        Set<String> runtime = Set.copyOf(com.tkisor.nekojs.core.dynamic.plan.DynamicBuilderContract
                .of(com.tkisor.nekojs.core.dynamic.plan.DynamicItemBuilder.class).memberNames());
        assertEquals(runtime, declared, "声明面成员与 runtime 契约同一输入派生");
    }

    @Test
    void typeScriptAndPythonBuilderDeclarationsRenderTheSameMemberSemantics() {
        String ts = RegistryBuilderTsRenderer.render(DynamicBuilderSurfaces.derive());
        String py = RegistryBuilderPyRenderer.render(DynamicBuilderSurfaces.derive());

        for (String builder : List.of("DynamicItemBuilder", "DynamicSoundEventBuilder", "DynamicMobEffectBuilder")) {
            assertEquals(1, count("interface " + builder + " {", ts), builder + " TS 接口恰好一次");
            assertEquals(1, count("class " + builder + "(Protocol):", py), builder + " Python 类恰好一次");
        }
        // The two setter forms and the property members agree on both sides: the same member name appears in each.
        for (String member : List.of("maxStackSize", "rarity", "fireResistant", "fixedRange", "category",
                "color", "mode")) {
            assertTrue(ts.contains(member), "TS 声明含成员 " + member);
            assertTrue(py.contains(member), "Python 声明含成员 " + member);
        }
        assertFalse(ts.contains("registry_types"), "动态 builder 条目与启动 registry_types 清单无关（独立路径）");
    }

    @Test
    void inheritedFluentModeSetterKeepsTheConcreteBuilderTypeInBothLanguages() {
        String ts = RegistryBuilderTsRenderer.render(DynamicBuilderSurfaces.derive());
        String py = RegistryBuilderPyRenderer.render(DynamicBuilderSurfaces.derive());
        for (String builder : List.of("DynamicItemBuilder", "DynamicMobEffectBuilder", "DynamicSoundEventBuilder")) {
            assertTrue(ts.contains("setMode(mode: string): " + builder + ";"),
                    "TypeScript fluent mode setter must keep " + builder);
            assertTrue(py.contains("def setMode(self, mode: str) -> " + builder + ": ..."),
                    "Python fluent mode setter must keep " + builder);
        }
    }

    @Test
    void nullableSoundRangeAppearsInPropertyAndSetterDeclarations() {
        String typescript = RegistryBuilderTsRenderer.render(DynamicBuilderSurfaces.derive());
        String python = RegistryBuilderPyRenderer.render(DynamicBuilderSurfaces.derive());

        assertTrue(typescript.contains("fixedRange: number | null;"),
                "TypeScript sound range property must permit its null default");
        assertTrue(typescript.contains("setFixedRange(range: number | null): DynamicSoundEventBuilder;"),
                "TypeScript sound range setter must permit explicit null and keep the concrete builder type");
        assertTrue(python.contains("fixedRange: float | None"),
                "Python sound range property must permit its null default");
        assertTrue(python.contains("def setFixedRange(self, range: float | None) -> DynamicSoundEventBuilder: ..."),
                "Python sound range setter must permit explicit null and keep the concrete builder type");
        assertTrue(typescript.lines().map(String::trim).anyMatch("maxStackSize: number;"::equals),
                "TypeScript primitive stack size must remain exactly non-nullable number");
        assertTrue(python.lines().map(String::trim).anyMatch("maxStackSize: int"::equals),
                "Python primitive stack size must remain exactly non-nullable int");
        assertFalse(typescript.contains("maxStackSize: number | null"),
                "TypeScript primitive stack size must not become nullable");
        assertFalse(python.contains("maxStackSize: int | None"),
                "Python primitive stack size must not become nullable");
    }

    private static int count(String needle, String haystack) {
        int count = 0;
        int index = 0;
        while ((index = haystack.indexOf(needle, index)) != -1) {
            count++;
            index += needle.length();
        }
        return count;
    }

    private static final class StubRuntime implements IPluginRuntime {
        private final Map<String, com.tkisor.nekojs.api.event.EventGroup> eventGroups;

        StubRuntime(Map<String, com.tkisor.nekojs.api.event.EventGroup> eventGroups) {
            this.eventGroups = eventGroups;
        }

        @Override public Map<String, Binding> bindings(ScriptType type) { return Map.of(); }
        @Override public Map<String, com.tkisor.nekojs.api.event.EventGroup> eventGroups() { return eventGroups; }
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
