package com.tkisor.nekojs.probe;

import com.tkisor.nekojs.api.JSTypeAdapter;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.catalog.EventCatalogEntry;
import com.tkisor.nekojs.api.catalog.ManualDeclarationCatalogEntry;
import com.tkisor.nekojs.api.catalog.NekoScriptCatalog;
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
import com.tkisor.nekojs.core.dynamic.facade.DynamicRegistryEvents;
import com.tkisor.nekojs.core.dynamic.plan.DynamicBuilderSurfaces;
import com.tkisor.nekojs.core.dynamic.plan.DynamicDefinitionType;
import com.tkisor.nekojs.probe.backend.typescript.AdapterAliasGenerator;
import com.tkisor.nekojs.probe.backend.typescript.EventDeclarationGenerator;
import com.tkisor.nekojs.probe.backend.typescript.RegistryBuilderTsRenderer;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Golden evidence for the declaration surface of the dynamic registry facade: the production
 * probe renderer renders it and the result is frozen as a reviewable baseline. Normal test runs
 * only read the baseline; updating it goes through {@code :common:regenerateGoldens} and a
 * REGENERATE.md entry.
 *
 * <p>Two goldens:
 * <ul>
 *   <li>{@code nekojs/dynamic/dynamic-registry-events.expected.d.ts} - the event declarations.
 *       It freezes the naming decision: the working name {@code ServerEvents.dynamicRegistry}
 *       was not used; the implementation uses a separate group {@code DynamicRegistryEvents}
 *       with the member name {@code dynamicRegistry} (rationale in MIGRATION.md);</li>
 *   <li>{@code nekojs/dynamic/dynamic-builders.expected.d.ts} - typed callback Builder
 *       declarations for three frozen types ({@code DynamicBuilderContract} reflection to
 *       entries to the renderer; same source as the runtime member and fingerprint). The header
 *       is fixed text of the shared renderer, and the derived input for this entry is
 *       {@code DynamicBuilderContract} (see the REGENERATE.md row).</li>
 * </ul>
 *
 * <p><b>What is frozen is the declaration shape, not a capability conclusion</b>: this scope
 * only delivers a local inert plan; public activation is decided by the transaction and sync
 * gates, and types that are not verified are recorded as not verified rather than unavailable.
 * The test class lives in the probe package to reuse {@link ProbeGoldenSupport} and the existing
 * regenerate entry point, the same mechanism as {@code LegacyProbeCompatibilityTest}.
 */
class DynamicRegistryEventsDeclarationGoldenTest {

    private static final String GOLDEN_DIR = "/nekojs/dynamic/";

    @BeforeAll
    static void initPlatform() {
        TestPlatformInit.ensureInitialized();
    }

    @Test
    void facadeEventDeclarationMatchesGolden() throws Exception {
        EventGroupRegistry registry = new EventGroupRegistry.Impl();
        registry.register(DynamicRegistryEvents.GROUP);
        List<EventCatalogEntry> serverEntries =
                NekoScriptCatalog.events(new StubRuntime(registry.view()), ScriptType.SERVER);
        assertEquals(1, serverEntries.size(), "冻结的 facade 面恰好一条 SERVER 总线");

        TypeAliasRegistry aliases = new TypeAliasRegistry();
        String actual = new EventDeclarationGenerator(aliases, new AdapterAliasGenerator(aliases))
                .generate(serverEntries, ScriptType.SERVER);

        maybeRegenerate("dynamic-registry-events.expected.d.ts", actual);
        assertEquals(resource("dynamic-registry-events.expected.d.ts"), normalize(actual),
                "动态注册 facade 事件声明 golden 变了。确认是刻意契约变更后，"
                        + "走 :common:regenerateGoldens + REGENERATE.md 留痕");

        // Readability assertions for the naming decision: pinned explicitly outside the golden so a rename
        // cannot hide behind golden drift
        assertEquals(1, count("namespace DynamicRegistryEvents {", actual), "独立组名（票面命名决策）");
        assertEquals(1, count("function dynamicRegistry(", actual), "成员名 dynamicRegistry 恰好一次");
        assertFalse(actual.contains("ServerEvents.dynamicRegistry"),
                "spec 08 的工作名不作为最终声明出现（命名决策记录见 REPORT/MIGRATION）");
        assertFalse(actual.contains("CLIENT"), "SERVER-only 域：CLIENT 侧无声明");
    }

    @Test
    void dynamicBuilderDeclarationsMatchGoldenAndStayInsideTheFrozenTypes() throws Exception {
        String actual = RegistryBuilderTsRenderer.render(DynamicBuilderSurfaces.derive());

        maybeRegenerate("dynamic-builders.expected.d.ts", actual);
        assertEquals(resource("dynamic-builders.expected.d.ts"), normalize(actual),
                "动态 Builder 声明 golden 变了。确认是刻意契约变更后，"
                        + "走 :common:regenerateGoldens + REGENERATE.md 留痕");

        for (String builder : List.of("DynamicItemBuilder", "DynamicSoundEventBuilder", "DynamicMobEffectBuilder")) {
            assertEquals(1, count("interface " + builder + " {", actual), builder + " 恰好一次");
        }
        assertFalse(actual.contains("registry_types"), "动态 Builder 不与启动期 registry_types 清单耦合");
        for (DynamicDefinitionType type : DynamicDefinitionType.values()) {
            assertTrue(actual.contains(type.apiName()),
                    "声明保留类型直达入口名（sugar）" + type.apiName());
        }
    }

    // ---- Golden support (same mechanism as ProbeEventsSurfaceGoldenTest / LegacyProbeCompatibilityTest) ----

    private static void maybeRegenerate(String name, String actual) throws Exception {
        if (!ProbeGoldenSupport.regenerateEnabled()) return;
        Path dir = ProbeGoldenSupport.resourceDir(DynamicRegistryEventsDeclarationGoldenTest.class, GOLDEN_DIR);
        assertNotNull(dir, "golden resources must resolve to a file: URL");
        Files.writeString(dir.resolve(name), actual, StandardCharsets.UTF_8);
        Assumptions.assumeTrue(false, "goldens regenerated; review and commit");
    }

    private static String resource(String name) throws IOException {
        try (var in = DynamicRegistryEventsDeclarationGoldenTest.class.getResourceAsStream(GOLDEN_DIR + name)) {
            if (in == null) throw new IllegalStateException("Missing golden " + name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        }
    }

    private static String normalize(String value) {
        return value.replace("\r\n", "\n");
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
