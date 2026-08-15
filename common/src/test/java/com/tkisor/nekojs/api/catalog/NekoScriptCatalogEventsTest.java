package com.tkisor.nekojs.api.catalog;

import com.tkisor.nekojs.api.JSTypeAdapter;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.data.Binding;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.api.event.EventGroupRegistry;
import com.tkisor.nekojs.api.plugin.IPluginRuntime;
import com.tkisor.nekojs.api.recipe.RecipeLifecycleContext;
import com.tkisor.nekojs.api.recipe.RecipeNamespaceEntry;
import com.tkisor.nekojs.api.surface.ApiRuntimeView;
import com.tkisor.nekojs.api.surface.ApiSymbolId;
import com.tkisor.nekojs.api.surface.EnvironmentKey;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BUG 回归：{@link NekoScriptCatalog#events(IPluginRuntime)}（全量目录）必须为每条 bus
 * 恰好产出一条 {@link EventCatalogEntry}，并用 bus 的规范 {@link ScriptType} 打标签。
 *
 * <p>旧实现按 ScriptType 逐类型各收集一遍：同一条 SERVER bus 会被打上 STARTUP/SERVER/TEST
 * 多个标签重复入目录。side 过滤（{@code scriptType().test(side)}）会把多条同总线条目放进
 * 同一个 side 文件（startup 同时命中 STARTUP 与 SERVER 两个标签），导致生成的 Python
 * {@code .pyi} / TypeScript {@code .d.ts} 里同一事件重复声明（Pylance 报「方法声明被同名
 * 声明遮盖」，如 {@code ProbeEventsType.modifyType} 出现两次）。
 */
class NekoScriptCatalogEventsTest {

    @BeforeAll
    static void initPlatform() {
        TestPlatformInit.ensureInitialized();
    }

    @Test
    void eachBusAppearsExactlyOnceTaggedWithItsCanonicalScriptType() {
        EventGroupRegistry registry = new EventGroupRegistry.Impl();
        EventGroup group = EventGroup.of("ProbeEvents");
        group.server("modifyType", String.class);
        group.server("addGlobal", Integer.class);
        registry.register(group);

        List<EventCatalogEntry> events = NekoScriptCatalog.events(new StubRuntime(registry.view()));

        assertEquals(2, events.size(),
                "each bus must appear exactly once (previously: once per applicable script type)");
        assertTrue(events.stream().allMatch(e -> e.scriptType() == ScriptType.SERVER),
                "entries must be tagged with the bus's canonical script type, not the collection pass type");
        assertEquals(List.of("ProbeEvents.modifyType", "ProbeEvents.addGlobal"),
                events.stream().map(e -> e.group() + "." + e.name()).toList());
    }

    @Test
    void sideFilterAdmitsServerBusExactlyOncePerSide() {
        EventGroupRegistry registry = new EventGroupRegistry.Impl();
        EventGroup group = EventGroup.of("ProbeEvents");
        group.server("modifyType", String.class);
        registry.register(group);

        List<EventCatalogEntry> events = NekoScriptCatalog.events(new StubRuntime(registry.view()));

        // SERVER.test(STARTUP) == true（startup 接受 server API）→ startup 侧包含该 bus，
        // 但只能有一条（旧实现因 STARTUP/SERVER 双标签给了两条）。
        assertEquals(1, events.stream().filter(e -> e.scriptType().test(ScriptType.STARTUP)).count());
        assertEquals(1, events.stream().filter(e -> e.scriptType().test(ScriptType.SERVER)).count());
        assertEquals(1, events.stream().filter(e -> e.scriptType().test(ScriptType.TEST)).count());
        // CLIENT 侧不适用
        assertEquals(0, events.stream().filter(e -> e.scriptType().test(ScriptType.CLIENT)).count());
    }

    @Test
    void mixedSideGroupsKeepDistinctEntries() {
        EventGroupRegistry registry = new EventGroupRegistry.Impl();
        EventGroup group = EventGroup.of("Mixed");
        group.startup("early", String.class);
        group.server("late", Integer.class);
        registry.register(group);

        List<EventCatalogEntry> events = NekoScriptCatalog.events(new StubRuntime(registry.view()));

        assertEquals(2, events.size());
        // viewBuses() 迭代顺序不构成契约：按名字定位断言
        EventCatalogEntry early = events.stream().filter(e -> e.name().equals("early")).findFirst().orElseThrow();
        EventCatalogEntry late = events.stream().filter(e -> e.name().equals("late")).findFirst().orElseThrow();
        assertEquals(ScriptType.STARTUP, early.scriptType());
        assertEquals(ScriptType.SERVER, late.scriptType());
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
