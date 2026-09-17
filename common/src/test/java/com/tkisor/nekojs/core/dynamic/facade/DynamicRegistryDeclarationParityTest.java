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
 * 票 16 AC9 的证据链 fixture：调用者 Interface（facade 组/payload）、契约（builder 契约
 * 反射）、TS/Python declaration（probe 后端生产渲染器）与本地行为测试由<b>同一契约输入</b>
 * 派生——事件条目进 catalog（一组一条、SERVER side）、builder 条目与 runtime member/
 * fingerprint 同源，TS 与 Python 成员语义一致。
 *
 * <p>口径遵循票 14/15 惯例：TS/Python parity 以内存断言承载（Python 不入 golden）；
 * declaration 形状由 {@code DynamicRegistryEventsDeclarationGoldenTest} 冻结为
 * contract/golden（两份 golden：facade 事件声明 + 三个冻结类型的 Builder 声明，
 * 由生产 probe 渲染器渲染；golden 只冻结声明形状，能力结论见 baseline REPORT 能力表
 * ——公开激活仍由事务/同步 gate（票 21）裁决）。
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
            // sugarName = 事件直达入口名（event.item / event.soundEvent / event.mobEffect）
            assertTrue(DynamicDefinitionType.byApiName(entry.sugarName()).isPresent(),
                    "sugarName 是冻结的类型直达入口名: " + entry.sugarName());
        }
        // 条目成员与 runtime 契约同源（名字集合一致）
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
        // setter 双形态与属性成员两侧一致（同一成员名出现）
        for (String member : List.of("maxStackSize", "rarity", "fireResistant", "fixedRange", "category",
                "color", "mode")) {
            assertTrue(ts.contains(member), "TS 声明含成员 " + member);
            assertTrue(py.contains(member), "Python 声明含成员 " + member);
        }
        assertFalse(ts.contains("registry_types"), "动态 builder 条目与启动 registry_types 清单无关（独立路径）");
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
