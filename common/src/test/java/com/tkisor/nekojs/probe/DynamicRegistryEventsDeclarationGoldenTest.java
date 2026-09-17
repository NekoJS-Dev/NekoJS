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
 * 票 16 AC2/AC9 的 contract/golden 证据：动态注册 facade 的声明面由<b>生产 probe
 * 渲染器</b>渲染并冻结为可审阅基线（普通测试只读；更新走
 * {@code :common:regenerateGoldens} + REGENERATE.md 留痕）。
 *
 * <p>两份 golden：
 * <ul>
 *   <li>{@code nekojs/dynamic/dynamic-registry-events.expected.d.ts}——事件声明。它冻结
 *       <b>命名决策</b>（spec 08 工作名 {@code ServerEvents.dynamicRegistry} 未采用；
 *       实施采用独立组 {@code DynamicRegistryEvents}，成员名 {@code dynamicRegistry}，
 *       理由见 baseline REPORT「命名决策」节与 MIGRATION.md）；</li>
 *   <li>{@code nekojs/dynamic/dynamic-builders.expected.d.ts}——三个冻结类型的 typed
 *       callback Builder 声明（{@code DynamicBuilderContract} 反射 → 条目 → 渲染器；
 *       与 runtime member/fingerprint 同源）。头注是共享渲染器（ticket 15）的固定文本，
 *       本条目的派生输入是 {@code DynamicBuilderContract}（见 REGENERATE.md 登记行）。</li>
 * </ul>
 *
 * <p><b>冻结的是声明形状，不是能力结论</b>：本票只交付本地 inert 计划；公开激活由事务/
 * 同步 gate（票 21）裁决，capability 结论见 REPORT 能力表（未验证类型记 not verified，
 * 不改写为 unavailable）。本测试类住 probe 包以复用 {@link ProbeGoldenSupport} 与既有
 * regenerate 入口（与 {@code LegacyProbeCompatibilityTest} 同一机制）。
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

        // 命名决策的可读性断言（golden 之外仍显式钉住，避免 golden 漂移掩盖改名）
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

    // ---- golden 支撑（与 ProbeEventsSurfaceGoldenTest / LegacyProbeCompatibilityTest 同一机制） ----

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
