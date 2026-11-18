package com.tkisor.nekojs.script;

import com.tkisor.nekojs.api.JSTypeAdapter;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.catalog.ManualDeclarationCatalogEntry;
import com.tkisor.nekojs.api.catalog.TypeDocCatalogEntry;
import com.tkisor.nekojs.api.data.Binding;
import com.tkisor.nekojs.api.data.BindingExposure;
import com.tkisor.nekojs.api.data.ModuleBinding;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.api.event.EventGroupRegistry;
import com.tkisor.nekojs.api.event.ModuleEventGroup;
import com.tkisor.nekojs.api.plugin.IPluginRuntime;
import com.tkisor.nekojs.api.recipe.RecipeLifecycleContext;
import com.tkisor.nekojs.api.recipe.RecipeNamespaceEntry;
import com.tkisor.nekojs.api.surface.ApiRuntimeView;
import com.tkisor.nekojs.api.surface.ApiSymbolId;
import com.tkisor.nekojs.api.surface.EnvironmentKey;
import com.tkisor.nekojs.core.DefaultScriptEventBridge;
import com.tkisor.nekojs.core.NekoCoreContext;
import com.tkisor.nekojs.core.NekoSandboxFactory;
import com.tkisor.nekojs.core.compiler.ScriptCompilerRegistry;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.error.DefaultErrorTracker;
import com.tkisor.nekojs.core.fs.ClassFilter;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.module.NekoModulePipelineCache;
import com.tkisor.nekojs.core.lifecycle.NekoRuntimeRoot;
import com.tkisor.nekojs.script.prop.ScriptProperty;
import com.tkisor.nekojs.script.prop.ScriptPropertyRegistry;
import com.tkisor.nekojs.script.ScriptContainer;
import com.tkisor.nekojs.script.ScriptTypeEnv;
import com.tkisor.nekojs.testfixture.NekoModuleTestFixtures;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import graal.graalvm.polyglot.Engine;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EventModuleRuntimeTest {
    @BeforeAll
    static void initializePlatform() {
        // Platform and script loggers live for the test JVM, not for one temporary module workspace.
        TestPlatformInit.ensureInitialized();
    }

    public static final class Counter {
        private final Map<String, AtomicInteger> hits = new ConcurrentHashMap<>();
        public void hit(String tag) { hits.computeIfAbsent(tag, ignored -> new AtomicInteger()).incrementAndGet(); }
        int hits(String tag) { return hits.getOrDefault(tag, new AtomicInteger()).get(); }
    }

    @Test
    void realEsmImportPublishesEventsWithoutInstallingPluginGlobals() throws Exception {
        try (Harness harness = new Harness()) {
            harness.write("""
                    import { ModuleEvents as events } from 'nekojs:event/versioned/runtime-test';
                    if (typeof ModuleEvents !== 'undefined') throw new Error('Plugin event global leaked');
                    if (typeof events.privateEvent !== 'undefined') throw new Error('Private event leaked');
                    events.ping(event => Counter.hit('imported-listener'));
                    Counter.hit('entry');
                    """);
            harness.load();
            assertEquals(1, harness.counter.hits("entry"), "real ESM entry must execute");
            harness.ping.post("payload");
            assertEquals(1, harness.counter.hits("imported-listener"));
        }
    }

    @Test
    void commonJsSubscriptionsRetainActiveListenersOnFailureAndReplaceOnceOnSuccess() throws Exception {
        EventBusJS<String, Void> bus;
        try (Harness harness = new Harness("cjs")) {
            bus = harness.ping;
            harness.write(commonJsListener("active"));
            harness.load();
            bus.post("payload");
            assertEquals(1, harness.counter.hits("active"));

            harness.write(commonJsListener("discarded") + "throw new Error('candidate rejected');");
            var failure = assertThrows(com.tkisor.nekojs.core.lifecycle.NekoReloadException.class,
                    () -> harness.root.reload(ScriptType.SERVER));
            assertEquals(com.tkisor.nekojs.core.lifecycle.ReloadPhase.EXECUTION, failure.report().phase());
            bus.post("payload");
            assertEquals(2, harness.counter.hits("active"), "failed candidate must retain the active subscription");
            assertEquals(0, harness.counter.hits("discarded"), "discarded candidate must never receive events");

            harness.write(commonJsListener("replacement"));
            assertTrue(harness.root.reload(ScriptType.SERVER).success());
            bus.post("payload");
            bus.post("payload");
            assertEquals(2, harness.counter.hits("active"));
            assertEquals(2, harness.counter.hits("replacement"), "successful reload must install exactly one listener");
        }
        assertFalse(bus.hasListeners(), "root close must release imported subscriptions");
    }

    @Test
    void ownerNamedEventsCannotCollideWithOfficialEventsOrBindingAggregates() throws Exception {
        try (Harness harness = new Harness()) {
            harness.write("""
                    import { ModuleEvents as official } from 'nekojs:event/stable';
                    import { ModuleEvents as plugin } from 'nekojs:event/stable/events';
                    import { ModuleEvents as helper } from 'nekojs/plugins/events/api/stable';
                    import * as ordinary from 'nekojs/api/stable';
                    if (helper !== 42) throw new Error('Plugin Binding namespace collided');
                    if (official === plugin || official === helper) throw new Error('Event namespaces collided');
                    if (typeof ordinary.ModuleEvents !== 'undefined') throw new Error('Event leaked into Binding aggregate');
                    if (typeof ModuleEvents !== 'undefined') throw new Error('Event global leaked');
                    official.ping(event => Counter.hit('official'));
                    plugin.ping(event => Counter.hit('plugin'));
                    Counter.hit('unchanged-global-binding');
                    """);
            harness.load();
            assertEquals(1, harness.counter.hits("unchanged-global-binding"));
            harness.officialPing.post("official");
            assertEquals(1, harness.counter.hits("official"));
            assertEquals(0, harness.counter.hits("plugin"));
            harness.pluginPing.post("plugin");
            assertEquals(1, harness.counter.hits("official"));
            assertEquals(1, harness.counter.hits("plugin"));
        }
    }

    @Test
    void obsoleteAndMalformedEventRoutesFailInsteadOfAliasingModules() throws Exception {
        try (Harness harness = new Harness("js")) {
            harness.write("""
                    for (const route of [
                        'nekojs/api/stable/events', 'nekojs/api/versioned/events',
                        'nekojs/plugins/runtime-test/api/versioned/events',
                        'nekojs:event', 'nekojs:event/stabl',
                        'nekojs:event/stable/missing-owner',
                        'nekojs:event/versioned/runtime-test/extra'
                    ]) {
                        let rejected = false;
                        try { require(route); } catch (failure) { rejected = true; }
                        if (!rejected) throw new Error('Unexpected event route alias: ' + route);
                    }
                    Counter.hit('routes-rejected');
                    """);
            harness.load();
            assertEquals(1, harness.counter.hits("routes-rejected"));
        }
    }

    private static String commonJsListener(String tag) {
        return "const { ModuleEvents: events } = require('nekojs:event/versioned/runtime-test');\n"
                + "if (typeof ModuleEvents !== 'undefined') throw new Error('Plugin event global leaked');\n"
                + "events.ping(event => Counter.hit('" + tag + "'));\n";
    }

    private static final class Harness implements AutoCloseable {
        final Engine engine = Engine.newBuilder().build();
        final Counter counter = new Counter();
        final EventGroup group = EventGroup.of("ModuleEvents");
        final EventBusJS<String, Void> ping = group.server("ping", String.class, api -> api.versioned());
        final EventGroupRegistry.Impl registry = new EventGroupRegistry.Impl();
        final EventGroup officialGroup = EventGroup.of("ModuleEvents");
        final EventBusJS<String, Void> officialPing = officialGroup.server("ping", String.class, api -> api.stable());
        final EventGroup pluginGroup = EventGroup.of("ModuleEvents");
        final EventBusJS<String, Void> pluginPing = pluginGroup.server("ping", String.class, api -> api.stable());
        final NekoRuntimeRoot root;
        final ScriptManager manager;
        final Path scriptDir;
        final Path entry;

        Harness() throws Exception { this("mjs"); }

        Harness(String extension) throws Exception {
            NekoJSPaths paths = NekoJSPaths.get();
            Files.createDirectories(paths.serverScripts());
            scriptDir = Files.createTempDirectory(paths.serverScripts(), "event-module-");
            entry = scriptDir.resolve("entry." + extension);
            group.add("privateEvent", ScriptType.SERVER, EventBusJS.of(String.class));
            registry.scopedTo("runtime-test").register(group);
            registry.scopedTo("nekojs").register(officialGroup);
            registry.scopedTo("events").register(pluginGroup);
            IPluginRuntime runtime = new Runtime(counter, registry);
            DefaultScriptEventBridge bridge = new DefaultScriptEventBridge(null);
            bridge.setPluginRuntime(runtime);
            SandboxConfig config = new SandboxConfig(false, false, false, false, true, true, false, true);
            DefaultErrorTracker tracker = new DefaultErrorTracker(paths, config);
            ScriptCompilerRegistry compilers = ScriptCompilerRegistry.createRuntimeRegistry();
            NekoCoreContext core = new NekoCoreContext(engine, config, new ClassFilter(config), tracker);
            NekoModulePipelineCache cache = NekoModuleTestFixtures.newCache(paths, compilers, config);
            NekoSandboxFactory sandbox = new NekoSandboxFactory(core, paths, compilers, runtime, cache);
            ScriptPropertyRegistry.Impl properties = new ScriptPropertyRegistry.Impl();
            properties.register(ScriptProperty.AFTER);
            properties.register(ScriptProperty.MODLOADED);
            properties.register(ScriptProperty.DISABLE);
            properties.register(ScriptProperty.PRIORITY);
            properties.freeze();
            root = new NekoRuntimeRoot(core, runtime, bridge, properties, sandbox, cache);
            manager = root.createScriptManager(ScriptType.SERVER);
            manager.configurePackPartition(null, () -> List.of(new ScriptContainer(
                    ScriptTypeEnv.makeId(ScriptType.SERVER, entry), ScriptType.SERVER, entry, properties)));
        }

        void write(String source) throws Exception { Files.writeString(entry, source); }
        void load() { manager.discoverScripts(); manager.loadScripts(); }
        @Override public void close() throws Exception {
            try {
                try { root.close(); } finally { engine.close(); }
            } finally {
                try (var files = Files.walk(scriptDir)) {
                    for (Path path : files.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(path);
                }
            }
        }
    }

    private record Runtime(Counter counter, EventGroupRegistry.Impl registry) implements IPluginRuntime {
        @Override public Map<String, Binding> bindings(ScriptType type) { return Map.of("Counter", Binding.of("Counter", counter)); }
        @Override public List<ModuleBinding> moduleBindings(ScriptType type) {
            return List.of(new ModuleBinding("events", BindingExposure.Category.STABLE, type, Binding.of("ModuleEvents", 42)));
        }
        @Override public Map<String, EventGroup> eventGroups() { return registry.view(); }
        @Override public List<EventGroup> lifecycleEventGroups() { return registry.sources(); }
        @Override public List<ModuleEventGroup> moduleEventGroups() { return registry.moduleGroups(); }
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
