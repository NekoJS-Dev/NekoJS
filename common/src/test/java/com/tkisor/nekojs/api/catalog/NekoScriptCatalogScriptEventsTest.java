package com.tkisor.nekojs.api.catalog;

import com.tkisor.nekojs.api.JSTypeAdapter;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.data.Binding;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.api.event.EventGroupRegistry;
import com.tkisor.nekojs.api.event.ScriptEventDefinition;
import com.tkisor.nekojs.api.event.ScriptEventRegistry;
import com.tkisor.nekojs.api.plugin.IPluginRuntime;
import com.tkisor.nekojs.api.recipe.RecipeLifecycleContext;
import com.tkisor.nekojs.api.recipe.RecipeNamespaceEntry;
import com.tkisor.nekojs.api.surface.ApiRuntimeView;
import com.tkisor.nekojs.api.surface.ApiSymbolId;
import com.tkisor.nekojs.api.surface.EnvironmentKey;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ScriptEvents 动态注册的事件组必须进 {@link NekoScriptCatalog} 事件目录：
 * probe 的声明生成（.d.ts/.pyi）与 payload 反射 BFS 种子都来自 catalog，
 * 缺了动态条目则脚本注册的自定义事件组对 IDE 完全不可见。
 */
class NekoScriptCatalogScriptEventsTest {

    @BeforeAll
    static void initPlatform() {
        TestPlatformInit.ensureInitialized();
    }

    // ScriptEventRegistry 是静态全局：测试前后清理，避免污染同 JVM 其他测试的目录视图
    @BeforeEach
    void clearBefore() {
        clearAllDefinitions();
    }

    @AfterEach
    void clearAfter() {
        clearAllDefinitions();
    }

    @Test
    void dynamicScriptEventsAppearOncePerDefinitionTaggedWithTargetType() {
        registerDefinition("MyEvents", "myHurt", ScriptType.SERVER, String.class);
        registerDefinition("MyEvents", "clientTick", ScriptType.CLIENT, Integer.class);

        List<EventCatalogEntry> events = NekoScriptCatalog.events(new StubRuntime(Map.of()));

        assertEquals(List.of("MyEvents.myHurt", "MyEvents.clientTick"),
                events.stream().map(e -> e.group() + "." + e.name()).toList());
        EventCatalogEntry hurt = events.stream().filter(e -> e.name().equals("myHurt")).findFirst().orElseThrow();
        EventCatalogEntry tick = events.stream().filter(e -> e.name().equals("clientTick")).findFirst().orElseThrow();
        assertEquals(ScriptType.SERVER, hurt.scriptType());
        assertEquals(ScriptType.CLIENT, tick.scriptType());
        assertEquals(String.class, hurt.eventType());
        assertEquals(Integer.class, tick.eventType());
        assertTrue(!hurt.dispatchable(), "script events are plain buses (no dispatch key)");
    }

    @Test
    void sideFilteredOverloadOnlyIncludesApplicableTargets() {
        registerDefinition("MyEvents", "myHurt", ScriptType.SERVER, String.class);

        List<EventCatalogEntry> server = NekoScriptCatalog.events(new StubRuntime(Map.of()), ScriptType.SERVER);
        List<EventCatalogEntry> client = NekoScriptCatalog.events(new StubRuntime(Map.of()), ScriptType.CLIENT);

        assertEquals(1, server.stream().filter(e -> e.group().equals("MyEvents")).count());
        assertEquals(0, client.stream().filter(e -> e.group().equals("MyEvents")).count());
    }

    @Test
    void dynamicAndStaticGroupsCoexistWithoutDuplicates() {
        EventGroupRegistry registry = new EventGroupRegistry.Impl();
        EventGroup group = EventGroup.of("ProbeEvents");
        group.server("modifyType", String.class);
        registry.register(group);
        registerDefinition("MyEvents", "myHurt", ScriptType.SERVER, String.class);

        List<EventCatalogEntry> events = NekoScriptCatalog.events(new StubRuntime(registry.view()));

        assertEquals(2, events.size());
        assertEquals(1, events.stream().filter(e -> e.group().equals("MyEvents")).count());
        assertEquals(1, events.stream().filter(e -> e.group().equals("ProbeEvents")).count());
    }

    private static void registerDefinition(String group, String name, ScriptType target, Class<?> eventType) {
        ScriptEventRegistry.register(new StubRuntime(Map.of()), new ScriptEventDefinition(
                group, name, target, eventType.getName(), "test_scripts/catalog.js",
                EventBusJS.of(eventType), () -> {}));
    }

    private static void clearAllDefinitions() {
        for (ScriptType target : ScriptType.all()) {
            ScriptEventRegistry.clearDefinitions(target);
        }
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
