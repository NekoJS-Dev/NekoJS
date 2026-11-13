package com.tkisor.nekojs.core.lifecycle;

import com.tkisor.nekojs.api.JSTypeAdapter;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.catalog.ManualDeclarationCatalogEntry;
import com.tkisor.nekojs.api.catalog.TypeDocCatalogEntry;
import com.tkisor.nekojs.api.data.Binding;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.api.plugin.IPluginRuntime;
import com.tkisor.nekojs.api.recipe.RecipeLifecycleContext;
import com.tkisor.nekojs.api.recipe.RecipeNamespaceEntry;
import com.tkisor.nekojs.api.surface.ApiRuntimeView;
import com.tkisor.nekojs.api.surface.ApiSymbolId;
import com.tkisor.nekojs.api.surface.EnvironmentKey;
import com.tkisor.nekojs.core.NekoCoreContext;
import com.tkisor.nekojs.core.NekoSharedEngine;
import com.tkisor.nekojs.core.NekoSandboxFactory;
import com.tkisor.nekojs.core.ScriptEventBridge;
import com.tkisor.nekojs.core.compiler.NekoCompilationPipeline;
import com.tkisor.nekojs.core.compiler.ScriptCompilerRegistry;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.error.DefaultErrorTracker;
import com.tkisor.nekojs.core.fs.ClassFilter;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.error.SourceMapRegistry;
import com.tkisor.nekojs.core.module.NekoModulePipeline;
import com.tkisor.nekojs.core.module.NekoModulePipelineCache;
import com.tkisor.nekojs.core.module.NekoTrustContext;
import com.tkisor.nekojs.core.module.NekoEsmVirtualModuleRegistry;
import com.tkisor.nekojs.script.prop.ScriptPropertyRegistry;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import graal.graalvm.polyglot.Value;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Repeatable tests for the root lifecycle: independent roots do not pollute each other,
 * root-owned state is released when the root closes, an exception during close does not block
 * later cleanup, and closing twice does not run callbacks twice.
 *
 * <p>Process-level exception: NekoSharedEngine is shared within one process but must not
 * couple roots. This test asserts that the error side and the manager sets stay isolated while
 * both roots use the same NekoSharedEngine.get() instance.
 */
class NekoRuntimeRootLifecycleTest {

    /** A recording bridge: it counts bindEvents and clearListeners calls and can be configured to throw for one type. */
    static final class RecordingBridge implements ScriptEventBridge {
        final Map<ScriptType, Integer> bindEventsCalls = new ConcurrentHashMap<>();
        final Map<ScriptType, Integer> clearListenersCalls = new ConcurrentHashMap<>();
        volatile ScriptType throwOnClear;

        @Override
        public void bindEvents(Value bindings, ScriptType type) {
            bindEventsCalls.merge(type, 1, Integer::sum);
        }

        @Override
        public void clearListeners(ScriptType type) {
            if (type == throwOnClear) {
                throw new IllegalStateException("simulated clearListeners failure for " + type);
            }
            clearListenersCalls.merge(type, 1, Integer::sum);
        }
    }

    static final class StubPluginRuntime implements IPluginRuntime {
        @Override public Map<String, Binding> bindings(ScriptType type) { return Map.of(); }
        @Override public Map<String, EventGroup> eventGroups() { return Map.of(); }
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

    @BeforeAll
    static void initPlatform() {
        Path gameDir = Path.of(System.getProperty("java.io.tmpdir"), "nekojs-test-gamedir");
        TestPlatformInit.ensureInitialized(gameDir);
    }

    private static final class RootBuilder {
        final ScriptEventBridge bridge;
        final StubPluginRuntime pluginRuntime = new StubPluginRuntime();

        RootBuilder(ScriptEventBridge bridge) {
            this.bridge = bridge;
        }

        NekoRuntimeRoot build() {
            return build(NekoSharedEngine.get());
        }

        NekoRuntimeRoot build(graal.graalvm.polyglot.Engine engine) {
            NekoJSPaths paths = NekoJSPaths.get();
            SandboxConfig config = SandboxConfig.defaultConfig();
            DefaultErrorTracker tracker = new DefaultErrorTracker(paths, config);
            NekoCoreContext core = new NekoCoreContext(
                    engine, config, ClassFilter.INSTANCE, tracker);
            ScriptCompilerRegistry compilers = ScriptCompilerRegistry.createRuntimeRegistry();
            NekoModulePipelineCache cache = new NekoModulePipelineCache(
                    new NekoModulePipeline(new NekoCompilationPipeline(), compilers, config),
                    new SourceMapRegistry(paths.root()), new NekoEsmVirtualModuleRegistry(paths.root()),
                    NekoTrustContext.local());
            NekoSandboxFactory sandboxFactory = new NekoSandboxFactory(
                    core, paths, compilers, pluginRuntime, cache);
            return new NekoRuntimeRoot(core, pluginRuntime, bridge,
                    new ScriptPropertyRegistry.Impl(), sandboxFactory, cache);
        }
    }

    /** Two independent roots share the process-level NekoSharedEngine, but their error side and manager
     * sets do not pollute each other. */
    @Test
    void independentRootsDoNotPolluteEachOther() {
        RecordingBridge bridge1 = new RecordingBridge();
        RecordingBridge bridge2 = new RecordingBridge();
        NekoRuntimeRoot root1 = new RootBuilder(bridge1).build();
        NekoRuntimeRoot root2 = new RootBuilder(bridge2).build();

        // Process-level exception: the shared Engine is the same instance for every root.
        assertSame(NekoSharedEngine.get(), NekoSharedEngine.get());

        root1.createScriptManager(ScriptType.SERVER);
        root2.createScriptManager(ScriptType.SERVER);
        assertNotSame(root1.scriptManagerOf(ScriptType.SERVER), root2.scriptManagerOf(ScriptType.SERVER),
                "independent roots must own independent ScriptManagers");

        root1.errorTracker().recordCallbackError(ScriptType.SERVER, "isolation", new RuntimeException("root1 boom"));

        assertEquals(1, root1.errors().count(), "root1 sees its own error");
        assertEquals(0, root2.errors().count(), "root2 must not see root1's error");
        assertFalse(root2.errorTracker().hasErrors());
    }

    /** close flushes and releases root-owned state: the manager set is emptied and the bridge listeners
     * are cleared per type. */
    @Test
    void closeReleasesManagersAndClearsBridgeListeners() {
        RecordingBridge bridge = new RecordingBridge();
        NekoRuntimeRoot root = new RootBuilder(bridge).build();
        root.createScriptManager(ScriptType.SERVER);
        root.createScriptManager(ScriptType.CLIENT);
        assertNotNull(root.scriptManagerOrNull(ScriptType.SERVER));

        root.closeSilently();

        assertNull(root.scriptManagerOrNull(ScriptType.SERVER), "managers released on close");
        assertNull(root.scriptManagerOrNull(ScriptType.CLIENT), "managers released on close");
        // Closing the root releases the manager set, the bridge listeners and the resources.
        // ErrorTracker is deliberately **not** cleared by close (the error surface must outlive
        // close so diagnostics stay readable), and where its generation belongs is tracked
        // separately. This asserts the current behavior so a change in it gets noticed.
        assertEquals(0, root.errors().count(), "tracker keeps its lifetime contract across close (see ledger A9)");
        Set<ScriptType> cleared = bridge.clearListenersCalls.keySet();
        assertTrue(cleared.contains(ScriptType.STARTUP) && cleared.contains(ScriptType.SERVER)
                        && cleared.contains(ScriptType.CLIENT) && cleared.contains(ScriptType.TEST),
                "bridge listeners must be cleared for every ScriptType, got " + cleared);
    }

    /** A second close does not throw and does not touch the managers again. The second round of
     * callbacks comes from manager flush/close, which disappears once the manager set is empty;
     * the bridge's clearListeners is an idempotent registry cleanup and may run more than once. */
    @Test
    void doubleCloseIsSafe() {
        RecordingBridge bridge = new RecordingBridge();
        NekoRuntimeRoot root = new RootBuilder(bridge).build();
        root.createScriptManager(ScriptType.SERVER);

        root.closeSilently();
        assertTrue(bridge.clearListenersCalls.getOrDefault(ScriptType.SERVER, 0) >= 1,
                "first close must clear bridge listeners");

        assertDoesNotThrow(root::closeSilently);
        assertNull(root.scriptManagerOrNull(ScriptType.SERVER), "state stays released after double close");
        assertTrue(bridge.bindEventsCalls.isEmpty(), "no listener re-binding may happen after close");
    }

    /** An exception raised during cleanup does not stop the rest of the cleanup. The bridge throws
     * for TEST, yet every manager is still released and the exception is rethrown. */
    @Test
    void closeExceptionDoesNotBlockSubsequentCleanup() {
        RecordingBridge bridge = new RecordingBridge();
        bridge.throwOnClear = ScriptType.TEST;
        NekoRuntimeRoot root = new RootBuilder(bridge).build();
        root.createScriptManager(ScriptType.SERVER);
        root.createScriptManager(ScriptType.CLIENT);

        assertThrows(RuntimeException.class, root::closeSilently,
                "closeSilently rethrows the first cleanup failure after finishing teardown");

        assertNull(root.scriptManagerOrNull(ScriptType.SERVER), "manager cleanup must complete despite bridge failure");
        assertNull(root.scriptManagerOrNull(ScriptType.CLIENT), "manager cleanup must complete despite bridge failure");
    }

    @Test
    void bridgeCleanupFailureDoesNotSkipOtherTypes() {
        RecordingBridge bridge = new RecordingBridge();
        bridge.throwOnClear = ScriptType.STARTUP;
        NekoRuntimeRoot root = new RootBuilder(bridge).build();
        assertThrows(RuntimeException.class, root::close);
        for (ScriptType type : ScriptType.values()) {
            if (type != ScriptType.STARTUP) assertEquals(1, bridge.clearListenersCalls.get(type));
        }
        assertTrue(root.globalState().isClosed());
    }

    @Test
    void listenerReleaseFailureStillClosesTheActiveContextAndManagerRoute() {
        RecordingBridge bridge = new RecordingBridge();
        try (var engine = graal.graalvm.polyglot.Engine.newBuilder().build()) {
            NekoRuntimeRoot root = new RootBuilder(bridge).build(engine);
            var manager = root.createScriptManager(ScriptType.SERVER);
            graal.graalvm.polyglot.Context context;
            Value callback;
            try (var candidate = manager.preparePackCandidate(List.of(), ignored -> {})) {
                context = candidate.context();
                callback = context.eval("js", "(() => 42)");
                com.tkisor.nekojs.core.state.GenerationGlobals.publishBatch(List.of(candidate.globals()));
                candidate.commitRoute();
            }
            bridge.throwOnClear = ScriptType.SERVER;
            assertThrows(RuntimeException.class, root::close);
            assertThrows(IllegalStateException.class, callback::execute);
            assertThrows(IllegalStateException.class, () -> com.tkisor.nekojs.script.ScriptManager.from(context));
            assertNull(root.scriptManagerOrNull(ScriptType.SERVER));
            assertNull(manager.activeContextForPackOwner());
            assertTrue(root.globalState().isClosed());
            assertDoesNotThrow(manager::close);
        }
    }

    /** Minimal stand-in for a headless environment: CLIENT load/reload and error handling go through
     * the same root lifecycle entry points that NekoJSClient uses for F3+T and for its loadScripts
     * lambda. */
    @Test
    void clientLoadAndReloadThroughRootLifecycleEntry() {
        RecordingBridge bridge = new RecordingBridge();
        NekoRuntimeRoot root = new RootBuilder(bridge).build();
        root.createScriptManager(ScriptType.CLIENT);

        assertDoesNotThrow(() -> root.scriptManagerOf(ScriptType.CLIENT).loadScripts());
        assertDoesNotThrow(() -> root.reload(ScriptType.CLIENT));
        assertEquals(0, root.errors().count());
        root.errorTracker().recordCallbackError(ScriptType.CLIENT, "client_reload", new RuntimeException("simulated"));
        assertEquals(1, root.errors().count(), "client reload failure must land in the root error tracker");

        root.closeSilently();
    }

    @Test
    void builtinUiSurvivesClientReloadSuccessAndFailureButClosesWithItsRoot() {
        try (var engine = graal.graalvm.polyglot.Engine.create("js")) {
            NekoRuntimeRoot root = new RootBuilder(new RecordingBridge()).build(engine);
            var session = root.openBuiltinUiSession(Runnable::run);
            try {
                assertSame(engine, session.context().getEngine());
                assertTrue(session.errorDashboardFactory(new com.tkisor.nekojs.testfixture.I18nTestFacade((key, args) -> key)).canExecute());
                root.createScriptManager(ScriptType.CLIENT).loadScripts();
                assertTrue(root.reload(ScriptType.CLIENT).success());
                root.registerDomainCollector(new CandidateDomainCollector() {
                    public String domain() { return "builtin-ui-failure-fixture"; }
                    public ScriptType scriptType() { return ScriptType.CLIENT; }
                    public void collect(Handle handle) { throw new IllegalStateException("fixture candidate failure"); }
                });
                assertThrows(NekoReloadException.class, () -> root.reload(ScriptType.CLIENT));
                assertFalse(session.isClosed());
                assertEquals(42, session.context().eval("js", "21*2").asInt());
            } finally { root.closeSilently(); }
            assertTrue(session.isClosed());
            assertThrows(RuntimeException.class, () -> session.context().eval("js", "42"));
            assertThrows(IllegalStateException.class, () -> root.openBuiltinUiSession(Runnable::run));
        }
    }
}
