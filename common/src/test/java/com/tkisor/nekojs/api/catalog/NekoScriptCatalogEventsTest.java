package com.tkisor.nekojs.api.catalog;

import com.tkisor.nekojs.api.JSTypeAdapter;
import com.tkisor.nekojs.api.ScriptType;
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
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression test for {@link NekoScriptCatalog#events(IPluginRuntime)} (the full catalog): every
 * bus must produce exactly one {@link EventCatalogEntry}, tagged with the bus's canonical
 * {@link ScriptType}.
 *
 * <p>Collecting buses once per {@link ScriptType} tags the same SERVER bus with STARTUP, SERVER and
 * TEST, so the catalog holds several entries for one bus. The side filter
 * ({@code scriptType().test(side)}) then puts those entries in the same side file (startup matches
 * both the STARTUP and the SERVER tag), and the generated Python {@code .pyi} and TypeScript
 * {@code .d.ts} declare the same event twice (Pylance reports a method declaration shadowed by a
 * declaration of the same name, e.g. {@code ProbeEventsType.modifyType} appearing twice).
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
        group.add("modifyType", ScriptType.SERVER, EventBusJS.of(String.class));
        group.add("addGlobal", ScriptType.SERVER, EventBusJS.of(Integer.class));
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
        group.add("modifyType", ScriptType.SERVER, EventBusJS.of(String.class));
        registry.register(group);

        List<EventCatalogEntry> events = NekoScriptCatalog.events(new StubRuntime(registry.view()));

        // SERVER.test(STARTUP) == true (startup accepts the server API), so the startup side includes this
        // bus, but only once; tagging it with both STARTUP and SERVER would give two entries.
        assertEquals(1, events.stream().filter(e -> e.scriptType().test(ScriptType.STARTUP)).count());
        assertEquals(1, events.stream().filter(e -> e.scriptType().test(ScriptType.SERVER)).count());
        assertEquals(1, events.stream().filter(e -> e.scriptType().test(ScriptType.TEST)).count());
        // Does not apply to the CLIENT side
        assertEquals(0, events.stream().filter(e -> e.scriptType().test(ScriptType.CLIENT)).count());
    }

    @Test
    void mixedSideGroupsKeepDistinctEntries() {
        EventGroupRegistry registry = new EventGroupRegistry.Impl();
        EventGroup group = EventGroup.of("Mixed");
        group.add("early", ScriptType.STARTUP, EventBusJS.of(String.class));
        group.add("late", ScriptType.SERVER, EventBusJS.of(Integer.class));
        registry.register(group);

        List<EventCatalogEntry> events = NekoScriptCatalog.events(new StubRuntime(registry.view()));

        assertEquals(2, events.size());
        // viewBuses() iteration order is not guaranteed: locate entries by name in the assertions
        EventCatalogEntry early = events.stream().filter(e -> e.name().equals("early")).findFirst().orElseThrow();
        EventCatalogEntry late = events.stream().filter(e -> e.name().equals("late")).findFirst().orElseThrow();
        assertEquals(ScriptType.STARTUP, early.scriptType());
        assertEquals(ScriptType.SERVER, late.scriptType());
    }

    @Test
    void modulePublicationScopeMatchesTheRuntimeWithoutChangingSourceDiscovery() {
        var registry = new EventGroupRegistry.Impl().scopedTo("nekojs");
        var group = EventGroup.of("StableTicks");
        group.server("tickPre", String.class, api -> api.stable().scriptTypes(ScriptType.SERVER));
        registry.register(group);
        var runtime = new StubRuntime(registry.view()) {
            @Override public List<com.tkisor.nekojs.api.event.ModuleEventGroup> moduleEventGroups() { return registry.moduleGroups(); }
        };
        var snapshot = NekoScriptCatalog.snapshot(runtime);
        assertEquals(List.of(ScriptType.SERVER), snapshot.moduleEvents().stream().map(ModuleEventCatalogEntry::scriptType).toList());
        assertEquals(String.class, snapshot.moduleEvents().getFirst().event().eventType());
        assertTrue(snapshot.events().getFirst().scriptType().test(ScriptType.STARTUP));
    }

    @Test
    void ownersCanPublishIdenticalNamesWithoutExportingPrivateSources() {
        var registry = new EventGroupRegistry.Impl();
        var official = EventGroup.of("Changes");
        official.server("changed", String.class, api -> {
            api.versioned();
            api.stable().scriptTypes(ScriptType.SERVER);
        });
        official.add("privateSource", ScriptType.SERVER, EventBusJS.of(Integer.class));
        var addon = EventGroup.of("Changes");
        addon.server("changed", Integer.class, api -> api.versioned());
        registry.scopedTo("nekojs").register(official);
        registry.scopedTo("addon").register(addon);
        var runtime = new StubRuntime(registry.view()) {
            @Override public List<com.tkisor.nekojs.api.event.ModuleEventGroup> moduleEventGroups() {
                return registry.moduleGroups();
            }
        };
        var snapshot = NekoScriptCatalog.snapshot(runtime, ScriptType.SERVER);
        assertEquals(3, snapshot.events().size(), "all native sources remain available for discovery");
        assertEquals(3, snapshot.moduleEvents().size(), "only explicit publications become module exports");
        assertEquals(java.util.Set.of("nekojs:event/stable", "nekojs:event/versioned",
                        "nekojs:event/versioned/addon"),
                snapshot.moduleEvents().stream().map(ModuleEventCatalogEntry::moduleId).collect(java.util.stream.Collectors.toSet()));
        assertTrue(snapshot.moduleEvents().stream().allMatch(entry -> entry.event().name().equals("changed")));
        assertTrue(snapshot.bindings().isEmpty());
        assertTrue(snapshot.legacySurface().stream().noneMatch(symbol -> symbol.id().kind().equals("event")));
        var addonEntry = snapshot.moduleEvents().stream().filter(entry -> entry.owner().equals("addon")).findFirst().orElseThrow();
        assertEquals(Integer.class, addonEntry.event().eventType());
    }

    private static class StubRuntime implements IPluginRuntime {
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
