package com.tkisor.nekojs.core.plugin;

import com.tkisor.nekojs.api.JSTypeAdapter;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.catalog.ManualDeclarationCatalogEntry;
import com.tkisor.nekojs.api.catalog.TypeDocCatalogEntry;
import com.tkisor.nekojs.api.data.Binding;
import com.tkisor.nekojs.api.event.DispatchKey;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.api.event.EventGroupJS;
import com.tkisor.nekojs.api.plugin.IPluginRuntime;
import com.tkisor.nekojs.api.recipe.RecipeLifecycleContext;
import com.tkisor.nekojs.api.recipe.RecipeNamespaceEntry;
import com.tkisor.nekojs.api.surface.ApiRuntimeView;
import com.tkisor.nekojs.api.surface.ApiSymbolId;
import com.tkisor.nekojs.api.surface.EnvironmentKey;
import com.tkisor.nekojs.core.NekoCoreContext;
import com.tkisor.nekojs.core.NekoSandboxFactory;
import com.tkisor.nekojs.core.ScriptEventBridge;
import com.tkisor.nekojs.core.compiler.ScriptCompilerRegistry;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.error.DefaultErrorTracker;
import com.tkisor.nekojs.core.fs.ClassFilter;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.script.ScriptContextRegistry;
import com.tkisor.nekojs.script.ScriptEnvironmentFactory;
import com.tkisor.nekojs.script.ScriptManager;
import com.tkisor.nekojs.script.ScriptTypeEnv;
import com.tkisor.nekojs.script.prop.ScriptProperty;
import com.tkisor.nekojs.script.prop.ScriptPropertyRegistry;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.Engine;
import graal.graalvm.polyglot.Value;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ticket 08 (AC10/AC11) — a normal script reload must not re-run plugin bootstrap/freeze, and the
 * previous generation's session-scoped objects must not be able to operate on the new session.
 *
 * <p>Two different lifetimes are pinned here:
 * <ul>
 *   <li><b>process-level</b>: the {@link IPluginRuntime} product, its frozen binding snapshot and
 *       its extension products keep their identity across a reload;</li>
 *   <li><b>generation-level</b>: the script environment (Graal {@code Context}, its listeners and
 *       its timers) is replaced, and the old generation's objects are rejected rather than silently
 *       rebound to the new session.</li>
 * </ul>
 */
class PluginRuntimeReloadLifetimeTest {

    /** Event payload used by the fixture event group. */
    public static final class TestEvent {}

    /** Counter bound into the script so listener/timer invocations can be counted by tag. */
    public static final class Counter {
        private final Map<String, AtomicInteger> hits = new ConcurrentHashMap<>();

        public void hit(Object tag) {
            hits.computeIfAbsent(String.valueOf(tag), ignored -> new AtomicInteger()).incrementAndGet();
        }

        public int hitsOf(String tag) {
            AtomicInteger value = hits.get(tag);
            return value == null ? 0 : value.get();
        }
    }

    /**
     * Process-level plugin runtime double used by the harness. It records how often the frozen
     * binding product is handed to an environment, which is the observable proof that the same
     * process-level product is reused across generations.
     */
    static final class RecordingPluginRuntime implements IPluginRuntime {
        final Counter counter = new Counter();
        final EventGroup group = EventGroup.of("Addon08Events");
        final EventBusJS<TestEvent, Void> pingBus = group.server("ping", TestEvent.class);
        final Map<String, Binding> serverBindings = Map.of("Counter", Binding.of("Counter", counter));
        final AtomicInteger bindingsDelivered = new AtomicInteger();

        @Override
        public Map<String, Binding> bindings(ScriptType type) {
            bindingsDelivered.incrementAndGet();
            return type == ScriptType.SERVER ? serverBindings : Map.of();
        }

        @Override public Map<String, EventGroup> eventGroups() { return Map.of(group.name(), group); }
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

    /** Minimal bridge that binds the fixture event group and records listener sweeps. */
    static final class FixtureBridge implements ScriptEventBridge {
        final RecordingPluginRuntime runtime;
        final List<ScriptType> cleared = new CopyOnWriteArrayList<>();

        FixtureBridge(RecordingPluginRuntime runtime) {
            this.runtime = runtime;
        }

        @Override
        public void bindEvents(Value bindings, ScriptType type) {
            bindings.putMember("Addon08Events", new EventGroupJS(runtime.group, type));
        }

        @Override
        public void clearListeners(ScriptType type) {
            cleared.add(type);
            runtime.group.clearListeners(type);
        }
    }

    @BeforeAll
    static void initPlatform() {
        Path gameDir = Path.of(System.getProperty("java.io.tmpdir"), "nekojs-test-gamedir");
        TestPlatformInit.ensureInitialized(gameDir);
    }

    @BeforeEach
    void cleanScriptDir() throws Exception {
        Path dir = ScriptTypeEnv.scriptsDir(ScriptType.SERVER);
        if (dir == null) return;
        Files.createDirectories(dir);
        try (var stream = Files.list(dir)) {
            for (Path path : stream.toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static ScriptPropertyRegistry newPropertyRegistry() {
        var impl = new ScriptPropertyRegistry.Impl();
        impl.register(ScriptProperty.AFTER);
        impl.register(ScriptProperty.MODLOADED);
        impl.register(ScriptProperty.DISABLE);
        impl.register(ScriptProperty.PRIORITY);
        impl.freeze();
        return impl;
    }

    /**
     * AC10: a normal reload must not re-run plugin bootstrap/freeze, and the process-level frozen
     * products must keep their identity, while generation-scoped state is replaced.
     */
    @Test
    void normalReloadKeepsProcessLevelPluginRuntimeAndReplacesGenerationSession() throws Exception {
        RecordingPluginRuntime pluginRuntime = new RecordingPluginRuntime();
        FixtureBridge bridge = new FixtureBridge(pluginRuntime);
        ScriptType scriptType = ScriptType.SERVER;
        NekoJSPaths paths = NekoJSPaths.get();
        SandboxConfig config = SandboxConfig.defaultConfig();
        Engine engine = Engine.newBuilder().build();

        try {
            DefaultErrorTracker tracker = new DefaultErrorTracker(paths, config);
            ScriptCompilerRegistry compilers = ScriptCompilerRegistry.createRuntimeRegistry();
            NekoCoreContext core = new NekoCoreContext(engine, config, new ClassFilter(config), tracker);
            NekoSandboxFactory sandboxFactory =
                    new NekoSandboxFactory(core, paths, compilers, pluginRuntime);
            ScriptEnvironmentFactory factory =
                    new ScriptEnvironmentFactory(bridge, pluginRuntime, sandboxFactory);
            ScriptManager manager = new ScriptManager(scriptType, bridge, pluginRuntime,
                    newPropertyRegistry(), tracker, paths, config, factory);

            Path scriptsDir = ScriptTypeEnv.scriptsDir(scriptType);
            Files.writeString(scriptsDir.resolve("entry.js"), """
                    Counter.hit('v1-entry');
                    Addon08Events.ping(function (event) { Counter.hit('v1-listener'); });
                    setInterval(function () { Counter.hit('v1-timer'); }, 30);
                    """);
            manager.discoverScripts();
            manager.loadScripts();

            ScriptType deliveredType = scriptType;
            Map<String, Binding> frozenSnapshot = pluginRuntime.bindings(deliveredType);
            Object firstRuntimeView = pluginRuntime.serverBindings;
            long generationBeforeReload = manager.generationId();
            Context generationV1 = contextOf(manager);
            assertEquals(1, pluginRuntime.counter.hitsOf("v1-entry"));
            pluginRuntime.pingBus.post(new TestEvent());
            assertEquals(1, pluginRuntime.counter.hitsOf("v1-listener"), "generation v1 listener is live");

            Files.writeString(scriptsDir.resolve("entry.js"), """
                    Counter.hit('v2-entry');
                    Addon08Events.ping(function (event) { Counter.hit('v2-listener'); });
                    setInterval(function () { Counter.hit('v2-timer'); }, 30);
                    """);
            manager.reloadScripts();

            // AC10: process-level products are the *same instances*; bootstrap/freeze did not re-run.
            assertSame(frozenSnapshot, pluginRuntime.serverBindings,
                    "a normal reload must not rebuild the frozen binding product");
            assertSame(firstRuntimeView, pluginRuntime.serverBindings,
                    "the process-level Plugin Runtime product keeps its identity across reload");
            assertSame(pluginRuntime.counter, frozenSnapshot.get("Counter").value(),
                    "the shared Java object inside the frozen binding is the same instance after reload");
            assertTrue(manager.generationId() > generationBeforeReload,
                    "a successful reload must advance the script generation");

            // AC10: the generation session object was replaced...
            Context generationV2 = contextOf(manager);
            assertNotSame(generationV1, generationV2, "the generation session (Context) must be replaced");

            // AC11: ...and the old generation can no longer operate the new session.
            IllegalStateException stale = assertThrows(IllegalStateException.class,
                    () -> ScriptManager.from(generationV1),
                    "the retired generation's Context must not resolve to the new session's manager");
            assertTrue(stale.getMessage().contains("No ScriptManager registered"), stale.getMessage());
            assertEquals(ScriptType.SERVER, ScriptContextRegistry.scriptTypeOf(generationV2),
                    "the new generation's Context is the registered session");
            assertEquals(null, ScriptContextRegistry.scriptTypeOf(generationV1),
                    "the retired generation's Context must be unregistered, not silently rebound");

            // AC11: the retired generation's event token cannot silently serve the new session.
            int v1Before = pluginRuntime.counter.hitsOf("v1-listener");
            int v2Before = pluginRuntime.counter.hitsOf("v2-listener");
            pluginRuntime.pingBus.post(new TestEvent());
            assertEquals(v1Before, pluginRuntime.counter.hitsOf("v1-listener"),
                    "the retired generation's listener must not receive new events");
            assertEquals(v2Before + 1, pluginRuntime.counter.hitsOf("v2-listener"),
                    "exactly the new generation's listener serves the event (no double execution)");

            // AC11: the retired generation's timers are not dispatched either.
            Thread.sleep(120);
            int v1TimerBefore = pluginRuntime.counter.hitsOf("v1-timer");
            manager.flushReadyNodeTimers();
            Thread.sleep(120);
            manager.flushReadyNodeTimers();
            assertEquals(v1TimerBefore, pluginRuntime.counter.hitsOf("v1-timer"),
                    "the retired generation's timer must not be dispatched by the new session");
            assertTrue(pluginRuntime.counter.hitsOf("v2-timer") > 0,
                    "the new generation's timer must be dispatched by the new session");

            // The engine swept the old generation's listeners exactly once at the commit point.
            assertEquals(1, bridge.cleared.stream().filter(t -> t == ScriptType.SERVER).count(),
                    "the commit point sweeps the old generation listeners exactly once");

            manager.close();
        } finally {
            engine.close();
        }
    }

    private static Context contextOf(ScriptManager manager) throws Exception {
        Field field = ScriptManager.class.getDeclaredField("runtime");
        field.setAccessible(true);
        Object environment = field.get(manager);
        var accessor = environment.getClass().getDeclaredMethod("context");
        accessor.setAccessible(true);
        return (Context) accessor.invoke(environment);
    }
}
