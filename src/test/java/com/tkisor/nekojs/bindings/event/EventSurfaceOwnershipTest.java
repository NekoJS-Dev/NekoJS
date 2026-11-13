// TODO(fabric): the fabric side has no counterpart yet; drop this whole-file guard once the port lands
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
 * Pins the real plugin registration surface: no duplicated bus and no copied event domain.
 *
 * <p>Builds the full event group set through the production registration path
 * ({@link NekoJSCorePlugin#registerEvents} plus {@code registerClientEvents}), then freezes it:
 * <ul>
 *   <li>every known event domain (recipe/loot/tags/capability/goal/render...) is still carried by
 *       its original {@link EventGroup}, and the group names have no duplicates;</li>
 *   <li>each bus appears once: no bus instance (identity) repeats across groups, the
 *       catalog-derived (group, name) pairs are unique, and the entry count equals the sum of
 *       each group's bus count;</li>
 *   <li>the three declaration surfaces do not copy event domains: the {@code ScriptEvents} group
 *       carries only the declaration entries (the server and client registration buses), the
 *       {@code ProbeEvents} group carries only the 4 probe-only buses, and {@code NativeEvents}
 *       is not an event group (it is a STARTUP raw binding, see
 *       {@code NativeEventsLegacyTierCharacterizationTest}).</li>
 * </ul>
 */
class EventSurfaceOwnershipTest {

    private static Map<String, EventGroup> registeredGroups() {
        // Two production-order installs must happen before any family class-inits, mirroring the
        // NekoJSMod constructor order:
        // 1) the cancellability predicate, otherwise the shared test JVM freezes every cancellable
        //    family bus as non-cancellable;
        // 2) a stub platform, otherwise KeyBindEvents' class-init reaches NekoJSMod's static block
        //    and installs NeoForgePlatform, whose gameDir needs a real FML launch, so every later
        //    test that asks scriptsDir for a harness would NPE.
        EventBusJS.setExternalCancellabilityPredicate(
                net.neoforged.bus.api.ICancellableEvent.class::isAssignableFrom);
        try {
            com.tkisor.nekojs.platform.Platform.init(new com.tkisor.nekojs.platform.IPlatform() {
                @Override public boolean isClient() { return false; }
                @Override public boolean isDevelopment() { return true; }
                @Override public String getMcVersion() { return "test"; }
                @Override public java.nio.file.Path getGameDir() {
                    return com.tkisor.nekojs.TestGameDirs.unique("nekojs-event-surface-ownership");
                }
                @Override public Map<String, com.tkisor.nekojs.platform.IModInfo> getMods() { return Map.of(); }
                @Override public com.tkisor.nekojs.platform.IModInfo getInfo(String modID) { return null; }
                @Override public String getLoaderId() { return "test"; }
                @Override public String getLoaderVersion() { return "0"; }
            });
        } catch (IllegalStateException alreadyInitialized) {
            // same-JVM reuse of an already initialized platform is fine
        }
        EventGroupRegistry registry = new EventGroupRegistry.Impl().scopedTo("nekojs");
        new NekoJSCorePlugin().registerEvents(registry);
        new NekoJSCorePlugin().registerClientEvents(registry);
        return registry.view();
    }

    @Test
    void domainGroupsStayInTheirOriginalOwnersWithoutDuplication() {
        Map<String, EventGroup> groups = registeredGroups();

        // The original owner of each known event domain. KeyBindEvents is a client group for >=26,
        // matching the guard in NekoJSCorePlugin.registerClientEvents, and is not registered on
        // 1.21.1.
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

        // The domain events are still there: spot-check that the recipe domain stays in
        // ServerEvents and that the goal/capability domains are not empty.
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

        // Bus identity is unique across groups: one EventBusJS must not hang in two groups or two
        // catalog entries (a second bus, or a copy).
        // BusHolder.getBus(type) returns the same instance for every applicable side, so
        // identities are collected per holder.
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

        // Catalog derivation: the (group, name) pairs are unique and the entry count equals the
        // total number of buses.
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

        // The ScriptEvents group is the declaration entry surface (the server/client registration
        // buses); it carries no game event domain.
        assertEquals(Set.of("server", "client"), groups.get("ScriptEvents").viewBuses().keySet(),
                "ScriptEvents must stay the declaration entry surface (server/client registration buses only)");

        // The ProbeEvents group is the probe extension surface (4 probe-only buses); it copies no
        // event domain.
        assertEquals(Set.of("modifyType", "assignType", "addGlobal", "snippets"),
                groups.get("ProbeEvents").viewBuses().keySet(),
                "ProbeEvents must stay probe-only; existing domains are not copied into it");

        // NativeEvents is not an event group: it is the raw binding face and does not go through
        // registerEvents.
        assertFalse(groups.containsKey("NativeEvents"),
                "NativeEvents must not become an event group (legacy/raw binding face)");

        // The dynamic declaration surface rejects a copied built-in group name at registration
        // time, so the conflict is diagnosable.
        List<String> conflicts = new ArrayList<>();
        for (String builtin : List.of("ServerEvents", "ProbeEvents")) {
            try {
                com.tkisor.nekojs.api.event.ScriptEventRegistry.validateAvailable(
                        new StubRuntime(groups), ScriptType.SERVER, builtin, "anything");
                conflicts.add(builtin);
            } catch (IllegalArgumentException expected) {
                // The built-in name is rejected with a diagnosable IllegalArgumentException.
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
