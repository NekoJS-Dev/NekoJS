// TODO(fabric): fabric 侧还没有对应实现，整文件守卫等移植完成后去掉
//? if neoforge {
package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.api.JSTypeAdapter;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.catalog.EventCatalogEntry;
import com.tkisor.nekojs.api.catalog.ManualDeclarationCatalogEntry;
import com.tkisor.nekojs.api.catalog.NekoScriptCatalog;
import com.tkisor.nekojs.api.catalog.TypeDocCatalogEntry;
import com.tkisor.nekojs.api.data.Binding;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.api.event.EventGroupRegistry;
import com.tkisor.nekojs.api.plugin.IPluginRuntime;
import com.tkisor.nekojs.api.recipe.RecipeLifecycleContext;
import com.tkisor.nekojs.api.recipe.RecipeNamespaceEntry;
import com.tkisor.nekojs.api.surface.ApiRuntimeView;
import com.tkisor.nekojs.api.surface.ApiSymbolId;
import com.tkisor.nekojs.api.surface.EnvironmentKey;
import com.tkisor.nekojs.core.NekoJSCorePlugin;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 票 14 AC6：真实插件注册面上的「无重复 bus / 无事件域复制」fixture。
 *
 * <p>以生产注册路径（{@link NekoJSCorePlugin#registerEvents} +
 * {@code registerClientEvents}）构造完整事件组集合，然后冻结：
 * <ul>
 *   <li>每个已知事件域（recipe/loot/tags/capability/goal/render…）仍由其原有
 *       {@link EventGroup} 承载，组名集合无重复；</li>
 *   <li>每条 bus 只出现一次：跨组无重复 bus 实例（identity），catalog 派生的
 *       (group, name) 对无重复，条目数 = 各组总线数之和；</li>
 *   <li>三类声明面不复制事件域：{@code ScriptEvents} 组只承载声明入口
 *       （server/client 两条注册总线），{@code ProbeEvents} 组只承载 4 条 probe-only
 *       总线，{@code NativeEvents} 不是事件组（它是 STARTUP raw binding，
 *       见 {@code NativeEventsLegacyTierCharacterizationTest}）。</li>
 * </ul>
 */
class EventSurfaceOwnershipTest {

    private static Map<String, EventGroup> registeredGroups() {
        EventGroupRegistry registry = new EventGroupRegistry.Impl();
        new NekoJSCorePlugin().registerEvents(registry);
        new NekoJSCorePlugin().registerClientEvents(registry);
        return registry.view();
    }

    @Test
    void domainGroupsStayInTheirOriginalOwnersWithoutDuplication() {
        Map<String, EventGroup> groups = registeredGroups();

        // 已知事件域的原有 owner。KeyBindEvents 是 >=26 客户端组（与
        // NekoJSCorePlugin.registerClientEvents 的同源守卫一致），1.21.1 不注册。
        Set<String> expected = new HashSet<>(Set.of(
                "PlayerEvents", "ServerEvents", "BlockEvents", "ItemEvents", "EntityEvents",
                "GoalEvents", "CommandEvents", "CapabilityEvents", "LevelEvents",
                "NetworkEvents", "ScriptEvents", "ProbeEvents", "ClientEvents"));
        //? if >=26 {
        expected.add("KeyBindEvents");
        //?}
        assertEquals(expected, groups.keySet(),
                "event domain ownership map changed: domains must stay in their original groups, "
                        + "not be copied into new surfaces");

        // 域内事件没有消失（spot check：recipe 域仍在 ServerEvents，goal/capability 域非空）
        assertTrue(groups.get("ServerEvents").viewBuses().containsKey("recipes"),
                "the recipe domain still lives in ServerEvents.recipes");
        assertFalse(groups.get("GoalEvents").viewBuses().isEmpty(),
                "the goal domain keeps its buses in GoalEvents");
        assertFalse(groups.get("CapabilityEvents").viewBuses().isEmpty(),
                "the capability domain keeps its buses in CapabilityEvents");
        assertFalse(groups.get("ClientEvents").viewBuses().isEmpty(),
                "the client/render domain keeps its buses in ClientEvents");
    }

    @Test
    void eachBusAppearsExactlyOnceAcrossGroupsAndCatalog() {
        Map<String, EventGroup> groups = registeredGroups();

        // 跨组 bus identity 唯一：同一条 EventBusJS 不得挂进两个组/两条目录（第二 bus / 复制）。
        // 注：BusHolder.getBus(type) 对每个适用 side 返回同一实例，identity 按 holder 收集。
        Set<EventBusJS<?, ?>> busIdentities = new HashSet<>();
        int totalBuses = 0;
        for (EventGroup group : groups.values()) {
            for (var holder : group.viewBuses().values()) {
                EventBusJS<?, ?> bus = null;
                for (ScriptType type : ScriptType.all()) {
                    bus = holder.getBus(type);
                    if (bus != null) break;
                }
                if (bus != null) {
                    assertTrue(busIdentities.add(bus),
                            "bus instance shared across groups/events: " + group.name());
                }
            }
            totalBuses += group.viewBuses().size();
        }
        assertTrue(totalBuses > 0);
        assertEquals(totalBuses, busIdentities.size(),
                "every registered bus must expose a live instance (no empty holders)");

        // catalog 派生：(group, name) 无重复，条目数 = 总线数
        List<EventCatalogEntry> events = NekoScriptCatalog.events(new StubRuntime(groups));
        assertEquals(totalBuses, events.size(),
                "catalog must derive exactly one entry per bus");
        Set<String> qualified = events.stream()
                .map(e -> e.group() + "." + e.name())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        assertEquals(events.size(), qualified.size(),
                "no duplicate (group, name) pairs in the catalog derivation");
    }

    @Test
    void declarationSurfacesDoNotCopyEventDomains() {
        Map<String, EventGroup> groups = registeredGroups();

        // ScriptEvents 组 = 声明入口面（server/client 注册总线），不承载任何游戏事件域
        assertEquals(Set.of("server", "client"), groups.get("ScriptEvents").viewBuses().keySet(),
                "ScriptEvents must stay the declaration entry surface (server/client registration buses only)");

        // ProbeEvents 组 = probe 扩展面（4 条 probe-only 总线），不复制任何事件域
        assertEquals(Set.of("modifyType", "assignType", "addGlobal", "snippets"),
                groups.get("ProbeEvents").viewBuses().keySet(),
                "ProbeEvents must stay probe-only; existing domains are not copied into it");

        // NativeEvents 不是事件组（raw binding 面，不经 registerEvents）
        assertFalse(groups.containsKey("NativeEvents"),
                "NativeEvents must not become an event group (legacy/raw binding face)");

        // 动态声明面在注册期就拒绝复制内置组名（可诊断冲突，AC3/AC6 交叉）
        List<String> conflicts = new ArrayList<>();
        for (String builtin : List.of("ServerEvents", "ProbeEvents")) {
            try {
                com.tkisor.nekojs.api.event.ScriptEventRegistry.validateAvailable(
                        new StubRuntime(groups), ScriptType.SERVER, builtin, "anything");
                conflicts.add(builtin);
            } catch (IllegalArgumentException expected) {
                // 可诊断拒绝
            }
        }
        assertTrue(conflicts.isEmpty(),
                "dynamic ScriptEvents declaration must reject built-in group names: " + conflicts);
    }

    private static final class StubRuntime implements IPluginRuntime {
        private final Map<String, EventGroup> eventGroups;

        StubRuntime(Map<String, EventGroup> eventGroups) {
            this.eventGroups = new LinkedHashMap<>(eventGroups);
        }

        @Override public Map<String, Binding> bindings(ScriptType type) { return Map.of(); }
        @Override public Map<String, EventGroup> eventGroups() { return eventGroups; }
        @Override public List<JSTypeAdapter<?>> adapters() { return List.of(); }
        @Override public List<TypeDocCatalogEntry> typeDocs() { return List.of(); }
        @Override public List<ManualDeclarationCatalogEntry> manualDeclarations() { return List.of(); }
        @Override public List<com.tkisor.nekojs.api.catalog.RegistryBuilderSurfaceEntry> registryBuilderSurfaces() { return List.of(); }
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
