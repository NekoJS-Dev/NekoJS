package com.tkisor.nekojs.probe;

import com.tkisor.nekojs.api.JSTypeAdapter;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.catalog.EventCatalogEntry;
import com.tkisor.nekojs.api.catalog.NekoScriptCatalog;
import com.tkisor.nekojs.api.catalog.ManualDeclarationCatalogEntry;
import com.tkisor.nekojs.api.catalog.TypeDocCatalogEntry;
import com.tkisor.nekojs.api.data.Binding;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.api.event.EventGroupRegistry;
import com.tkisor.nekojs.api.plugin.IPluginRuntime;
import com.tkisor.nekojs.api.recipe.RecipeLifecycleContext;
import com.tkisor.nekojs.api.recipe.RecipeNamespaceEntry;
import com.tkisor.nekojs.api.surface.ApiRuntimeView;
import com.tkisor.nekojs.api.surface.ApiSymbolId;
import com.tkisor.nekojs.api.surface.EnvironmentKey;
import com.tkisor.nekojs.probe.backend.python.ApiTypeRefPyRenderer;
import com.tkisor.nekojs.probe.backend.python.PythonEventRenderer;
import com.tkisor.nekojs.probe.backend.typescript.AdapterAliasGenerator;
import com.tkisor.nekojs.probe.backend.typescript.EventDeclarationGenerator;
import com.tkisor.nekojs.probe.events.ProbeEvents;
import com.tkisor.nekojs.probe.types.TypeAliasRegistry;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 票 14 AC5：{@link ProbeEvents} 限定为 Probe 扩展面——事件进入真实 catalog/declaration
 * golden，且不创建通用运行时事件或第二事件 bus。
 *
 * <p>钉住的契约：
 * <ul>
 *   <li>{@code ProbeEvents.GROUP} 的成员集合冻结为 4 条 probe-only 总线
 *       （modifyType/assignType/addGlobal/snippets），全部 SERVER side；</li>
 *   <li>catalog 派生（{@link NekoScriptCatalog#events}）：恰好 4 条、每条一次，
 *       payload 事件类 = probe 扩展事件；</li>
 *   <li>TS 声明 golden（{@code nekojs/probe/probe-events.expected.d.ts}，经
 *       {@code :common:regenerateGoldens} 显式再生成）：SERVER 侧包含全部 4 个成员，
 *       CLIENT 侧一条都没有（side 过滤不产生重复/错侧声明）；</li>
 *   <li>Python 声明（同一 entry 列表驱动）：4 个成员各恰好一次。</li>
 * </ul>
 *
 * <p>这些总线只在 probe 管线（{@code ProbeCoordinator}/{@code ProbeIrBuilder}）post，
 * 不承载任何游戏运行时事件——「不变成通用运行时事件」由成员集合冻结 + 唯一 post 来源
 * 保证（见本票 REPORT 的旁路清单）。
 */
class ProbeEventsSurfaceGoldenTest {

    private static final Set<String> PROBE_ONLY_MEMBERS =
            Set.of("modifyType", "assignType", "addGlobal", "snippets");

    @BeforeAll
    static void initPlatform() {
        TestPlatformInit.ensureInitialized();
    }

    @Test
    void probeEventsGroupMembersAreFrozenToProbeOnlyBuses() {
        assertEquals(PROBE_ONLY_MEMBERS, ProbeEvents.GROUP.viewBuses().keySet(),
                "ProbeEvents must stay a probe-extension-only surface: no general runtime events, no second bus");
        for (var entry : ProbeEvents.GROUP.viewBuses().entrySet()) {
            var holder = entry.getValue();
            assertTrue(holder.canApplyOn(ScriptType.SERVER),
                    entry.getKey() + " must stay SERVER-side (probe runs on the server thread)");
            assertFalse(holder.canApplyOn(ScriptType.CLIENT),
                    entry.getKey() + " must not leak into the CLIENT side");
        }
    }

    @Test
    void probeEventsEnterTheCatalogExactlyOnceEach() {
        EventGroupRegistry registry = new EventGroupRegistry.Impl();
        registry.register(ProbeEvents.GROUP);

        List<EventCatalogEntry> events = NekoScriptCatalog.events(new StubRuntime(registry.view()));

        assertEquals(4, events.size(), "one catalog entry per probe bus");
        for (EventCatalogEntry entry : events) {
            assertEquals("ProbeEvents", entry.group());
            assertTrue(PROBE_ONLY_MEMBERS.contains(entry.name()));
            assertEquals(ScriptType.SERVER, entry.scriptType(),
                    "catalog entries carry the bus's canonical side");
            assertFalse(entry.scriptDefined(), "probe events carry real payload event classes");
            assertNotNull(entry.eventType(), "probe events carry real payload event classes");
            assertFalse(entry.dispatchable(), "probe events are plain buses");
        }
    }

    @Test
    void probeEventsTypeScriptDeclarationMatchesGolden() throws Exception {
        EventGroupRegistry registry = new EventGroupRegistry.Impl();
        registry.register(ProbeEvents.GROUP);
        List<EventCatalogEntry> serverEntries = NekoScriptCatalog.events(
                new StubRuntime(registry.view()), ScriptType.SERVER);
        assertEquals(4, serverEntries.size(), "side-filtered server view keeps all probe events");

        TypeAliasRegistry aliases = new TypeAliasRegistry();
        String actual = new EventDeclarationGenerator(aliases, new AdapterAliasGenerator(aliases))
                .generate(serverEntries, ScriptType.SERVER);

        maybeRegenerate("probe-events.expected.d.ts", actual);
        assertEquals(resource("probe-events.expected.d.ts"), normalize(actual),
                "ProbeEvents TS declaration golden changed; regenerate + review via :common:regenerateGoldens");

        // golden 的可读性断言：4 个成员各恰好一次，且都是真实 payload 类型（非 any）
        for (String member : PROBE_ONLY_MEMBERS) {
            assertEquals(1, count("function " + member + "(", actual),
                    member + " must be declared exactly once");
        }
        assertFalse(actual.contains("payload?: any"), "probe events are not script-defined events");
    }

    @Test
    void clientSideFilterExcludesProbeEventsEntirely() {
        EventGroupRegistry registry = new EventGroupRegistry.Impl();
        registry.register(ProbeEvents.GROUP);

        List<EventCatalogEntry> clientEntries = NekoScriptCatalog.events(
                new StubRuntime(registry.view()), ScriptType.CLIENT);
        assertTrue(clientEntries.isEmpty(),
                "ProbeEvents are SERVER-side; the CLIENT side filter must not emit any entry (no duplicates either)");

        TypeAliasRegistry aliases = new TypeAliasRegistry();
        String clientDeclaration = new EventDeclarationGenerator(aliases, new AdapterAliasGenerator(aliases))
                .generate(clientEntries, ScriptType.CLIENT);
        assertFalse(clientDeclaration.contains("ProbeEvents"),
                "client-side declaration must not mention ProbeEvents at all");
    }

    @Test
    void probeEventsPythonDeclarationRendersEachMemberOnce() {
        EventGroupRegistry registry = new EventGroupRegistry.Impl();
        registry.register(ProbeEvents.GROUP);
        List<EventCatalogEntry> serverEntries = NekoScriptCatalog.events(
                new StubRuntime(registry.view()), ScriptType.SERVER);

        String py = new PythonEventRenderer(new ApiTypeRefPyRenderer(Set.of()), Set.of())
                .render(ScriptType.SERVER, serverEntries, null);
        assertEquals(1, count("class ProbeEventsType:", py), "one group class");
        for (String member : PROBE_ONLY_MEMBERS) {
            assertEquals(1, count("def " + member + "(", py),
                    member + " must render exactly once in the Python declaration");
        }
    }

    // ---- golden 支撑（与 LegacyProbeCompatibilityTest 同一机制） ----

    /** 重生成模式（-Dnekojs.golden.regenerate=true）：实际产物覆盖写回 golden 后跳过断言。 */
    private static void maybeRegenerate(String name, String actual) throws Exception {
        if (!ProbeGoldenSupport.regenerateEnabled()) return;
        Path dir = ProbeGoldenSupport.resourceDir(ProbeEventsSurfaceGoldenTest.class, "/nekojs/probe/");
        assertNotNull(dir, "golden resources must resolve to a file: URL");
        Files.writeString(dir.resolve(name), actual, StandardCharsets.UTF_8);
        Assumptions.assumeTrue(false, "goldens regenerated; review and commit");
    }

    private static String resource(String name) throws IOException {
        try (var in = ProbeEventsSurfaceGoldenTest.class.getResourceAsStream("/nekojs/probe/" + name)) {
            if (in == null) throw new IllegalStateException("Missing golden " + name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        }
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

    private static String normalize(String value) {
        return value.replace("\r\n", "\n");
    }

    private static final class StubRuntime implements IPluginRuntime {
        private final Map<String, EventGroup> eventGroups;

        StubRuntime(Map<String, EventGroup> eventGroups) {
            this.eventGroups = eventGroups;
        }

        @Override public Map<String, Binding> bindings(ScriptType type) { return Map.of(); }
        @Override public Map<String, EventGroup> eventGroups() { return eventGroups; }
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
