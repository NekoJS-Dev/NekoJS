package com.tkisor.nekojs.wrapper.event.server;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.catalog.EventCatalogEntry;
import com.tkisor.nekojs.api.data.Binding;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.api.plugin.IPluginRuntime;
import com.tkisor.nekojs.bindings.event.BlockEvents;
import com.tkisor.nekojs.bindings.event.ItemEvents;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 票 39 AC1 fixture：{@code ItemEvents.modification} / {@code BlockEvents.modification}
 * 的公开事件面（组名、事件名、payload 类、side、cancel/dispatch 能力）仍在既有
 * catalog 派生路径上——本票不新增第二事件 bus、不复制 wrapper，事件名与 payload 不变。
 * 无 vanilla 注册表依赖（只读总线元数据）→ 五节点真跑（block 半边 >=26）。
 *
 * <p>dispatch 时机（唯一 post 来源 = domain owner 的两个合法收集点）与 priority/顺序语义
 * 由 {@code ModificationDomainOwner} + {@code CandidateDomainCollector} 承载：本测试钉住
 * 静态声明面，运行面由 {@code Ticket39ModificationScriptE2ETest} 与
 * {@code Ticket39BlockModificationScriptE2ETest} 覆盖；source trace（谁 post）见票 39 REPORT。
 */
class Ticket39ModificationEventSurfaceTest {

    private static IPluginRuntime runtimeWith(Map<String, EventGroup> groups) {
        return new StubRuntime(groups);
    }

    private static Optional<EventCatalogEntry> entryOf(List<EventCatalogEntry> entries, String group, String name) {
        return entries.stream().filter(e -> e.group().equals(group) && e.name().equals(name)).findFirst();
    }

    private static void assertPostedObjectBus(EventCatalogEntry entry, Class<?> payload) {
        assertEquals(payload, entry.eventType(), "payload class is the collecting payload (unchanged public面)");
        assertEquals(ScriptType.SERVER, entry.scriptType(),
                "modification is a server-side, platform-posted bus");
        assertFalse(entry.cancellable(), "posted-object modification bus is not cancellable");
        assertFalse(entry.dispatchable(), "modification is not key-dispatched");
        assertFalse(entry.scriptDefined(), "modification is a native payload bus, not a script event");
    }

    @Test
    void modificationBusesAppearExactlyOnceInTheCatalogWithTheirPayloads() {
        Map<String, EventGroup> groups = new LinkedHashMap<>();
        groups.put("ItemEvents", ItemEvents.GROUP);
        groups.put("BlockEvents", BlockEvents.GROUP);
        List<EventCatalogEntry> entries = com.tkisor.nekojs.api.catalog.NekoScriptCatalog.events(runtimeWith(groups));

        EventCatalogEntry item = entryOf(entries, "ItemEvents", "modification").orElseThrow(
                () -> new AssertionError("ItemEvents.modification missing from the catalog"));
        assertPostedObjectBus(item, ItemModificationEventJS.class);
        assertEquals(1, entries.stream()
                        .filter(e -> e.group().equals("ItemEvents") && e.name().equals("modification")).count(),
                "exactly one catalog entry per bus (no duplicate declaration)");

//? if >=26 {
        EventCatalogEntry block = entryOf(entries, "BlockEvents", "modification").orElseThrow(
                () -> new AssertionError("BlockEvents.modification missing from the catalog"));
        assertPostedObjectBus(block, BlockModificationEventJS.class);
//?}
    }

    @Test
    void payloadSurfaceKeepsTheScriptVisibleDeclarationEntryPoint() throws Exception {
        // 脚本面公开成员保持不变：modify(id, callback) 与 getModifiedCount()
        assertEquals(void.class, ItemModificationEventJS.class
                .getMethod("modify", String.class, java.util.function.Consumer.class).getReturnType());
        assertTrue(ItemModificationEventJS.class.getMethod("getModifiedCount").getReturnType() == int.class);
//? if >=26 {
        assertEquals(void.class, BlockModificationEventJS.class
                .getMethod("modify", String.class, java.util.function.Consumer.class).getReturnType());
        assertTrue(BlockModificationEventJS.class.getMethod("getModifiedCount").getReturnType() == int.class);
//?}
    }

    private static final class StubRuntime implements IPluginRuntime {
        private final Map<String, EventGroup> groups;

        StubRuntime(Map<String, EventGroup> groups) {
            this.groups = groups;
        }

        @Override public Map<String, Binding> bindings(ScriptType type) { return Map.of(); }
        @Override public Map<String, EventGroup> eventGroups() { return groups; }
        @Override public List<com.tkisor.nekojs.api.JSTypeAdapter<?>> adapters() { return List.of(); }
        @Override public List<com.tkisor.nekojs.api.catalog.TypeDocCatalogEntry> typeDocs() { return List.of(); }
        @Override public List<com.tkisor.nekojs.api.catalog.ManualDeclarationCatalogEntry> manualDeclarations() { return List.of(); }
        @Override public Map<String, String> nodeModules() { return Map.of(); }
        @Override public Map<String, com.tkisor.nekojs.api.recipe.RecipeNamespaceEntry> recipeNamespaces() { return Map.of(); }
        @Override public void beforeRecipeLoading(com.tkisor.nekojs.api.recipe.RecipeLifecycleContext context) {}
        @Override public void afterRecipes(com.tkisor.nekojs.api.recipe.RecipeLifecycleContext context) {}
        @Override public void fireInit() {}
        @Override public void fireInitStartup() {}
        @Override public void fireAfterInit() {}
        @Override public void fireBeforeScriptsLoaded(ScriptType type) {}
        @Override public void fireAfterScriptsLoaded(ScriptType type) {}
        @Override public com.tkisor.nekojs.api.surface.ApiRuntimeView apiRuntime(com.tkisor.nekojs.api.surface.EnvironmentKey environment) { return null; }
        @Override public Object managedApiImplementation(com.tkisor.nekojs.api.surface.ApiSymbolId globalId) { return null; }
    }
}
