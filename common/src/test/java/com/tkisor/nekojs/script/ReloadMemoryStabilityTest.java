package com.tkisor.nekojs.script;

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
import com.tkisor.nekojs.core.NekoSandboxFactory;
import com.tkisor.nekojs.core.ScriptEventBridge;
import com.tkisor.nekojs.core.compiler.ScriptCompilerRegistry;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.error.DefaultErrorTracker;
import com.tkisor.nekojs.core.fs.ClassFilter;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.module.NekoModulePipelineCache;
import com.tkisor.nekojs.script.prop.ScriptProperty;
import com.tkisor.nekojs.script.prop.ScriptPropertyRegistry;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import graal.graalvm.polyglot.Engine;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks that 50 consecutive transactional reloads keep memory stable instead of growing.
 *
 * <p>Each reload creates a candidate Graal Context, runs the ESM entry (the virtual ESM registry,
 * prepared pipeline cache, and source maps all take part), and closes the old Context after the
 * commit. Known leak sources: leftover CONTEXT_TO_MANAGER / ScriptContextRegistry entries (the close
 * path now removes and unbinds them) and the unbounded generation count in
 * NekoEsmVirtualModuleRegistry (clear(type) resets it).
 *
 * <p>Assertion strategy: after every 10 reloads, force a GC and sample the used heap as one window.
 * The last window must not exceed the first by more than the tolerance; the tolerance covers the
 * one-time warm-up of JIT and Graal internals, and a sustained growth trend is not allowed. The
 * window sequence is printed to stdout so a CI failure can be diagnosed.
 */
class ReloadMemoryStabilityTest {

    private static final int RELOADS = 50;
    private static final int WINDOW = 10;
    /** Upper bound for the one-time warm-up growth; a real leak, which adds a fixed amount per reload,
     * goes far beyond it. */
    private static final long TOLERANCE_BYTES = 32L * 1024 * 1024;

    public static final class Recorder {
        private volatile String value;
        public void record(String value) { this.value = value; }
        public String value() { return value; }
    }

    private static final class StubPluginRuntime implements IPluginRuntime {
        private final Recorder recorder;
        StubPluginRuntime(Recorder recorder) { this.recorder = recorder; }

        @Override public Map<String, Binding> bindings(ScriptType type) {
            return Map.of("TestRecorder", Binding.of("TestRecorder", recorder));
        }
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
        // A fixed, non-temporary gameDir: FileAppender holds the log file handle until the JVM exits, so
        // cleanup of @TempDir fails
        Path gameDir = Path.of(System.getProperty("java.io.tmpdir"), "nekojs-test-gamedir");
        TestPlatformInit.ensureInitialized(gameDir);
    }

    @BeforeEach
    void cleanScriptDir() throws Exception {
        Path serverDir = NekoJSPaths.get().serverScripts();
        Files.createDirectories(serverDir);
        try (var stream = Files.list(serverDir)) {
            for (Path path : stream.toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    @Test
    void fiftyConsecutiveReloadsDoNotGrowMemoryMonotonically() throws Exception {
        NekoJSPaths paths = NekoJSPaths.get();
        Path serverDir = paths.serverScripts();
        Path entry = serverDir.resolve("entry.mjs");
        Path helper = serverDir.resolve("helper.mjs");
        Files.writeString(entry, """
                import { value } from './helper.mjs';
                TestRecorder.record(value);
                """);
        Files.writeString(helper, "export const value = 'stable';\n");

        Recorder recorder = new Recorder();
        Engine engine = Engine.newBuilder().build();
        SandboxConfig config = SandboxConfig.defaultConfig();
        DefaultErrorTracker tracker = new DefaultErrorTracker(paths, config);
        StubPluginRuntime pluginRuntime = new StubPluginRuntime(recorder);
        NekoCoreContext core = new NekoCoreContext(engine, config, new ClassFilter(config), tracker);
        ScriptCompilerRegistry compilers = ScriptCompilerRegistry.createRuntimeRegistry();
        NekoModulePipelineCache cache = com.tkisor.nekojs.testfixture.NekoModuleTestFixtures
                .newCache(paths, compilers, config);
        NekoSandboxFactory sandboxFactory = new NekoSandboxFactory(core, paths,
                compilers, pluginRuntime, cache);
        ScriptEnvironmentFactory environmentFactory =
                new ScriptEnvironmentFactory(ScriptEventBridge.EMPTY, pluginRuntime, sandboxFactory,
                        new com.tkisor.nekojs.core.state.GlobalStateStores());
        ScriptManager manager = new ScriptManager(ScriptType.SERVER, ScriptEventBridge.EMPTY, pluginRuntime,
                propertyRegistry(), tracker, paths, config, environmentFactory, List.of(), cache);

        long[] windows = new long[RELOADS / WINDOW];
        try {
            assertDoesNotThrow(() -> {
                manager.discoverScripts();
                manager.loadScripts();
                for (int i = 1; i <= RELOADS; i++) {
                    manager.reloadScripts();
                    assertEquals("stable", recorder.value(),
                            "reload #" + i + " must re-execute the entry and observe the helper export");
                    if (i % WINDOW == 0) {
                        windows[i / WINDOW - 1] = usedHeapAfterGc();
                        System.out.printf("[ReloadMemoryStability] after %2d reloads: used heap = %d KB%n",
                                i, windows[i / WINDOW - 1] / 1024);
                    }
                }
            });
        } finally {
            manager.close();
            engine.close();
            Files.deleteIfExists(entry);
            Files.deleteIfExists(helper);
        }

        long first = windows[0];
        long last = windows[windows.length - 1];
        assertTrue(last <= first + TOLERANCE_BYTES,
                "used heap after " + RELOADS + " reloads (" + (last / 1024) + " KB) grew by "
                        + ((last - first) / 1024) + " KB over the first window (" + (first / 1024)
                        + " KB) — sustained per-reload leak; windows="
                        + java.util.Arrays.toString(java.util.Arrays.stream(windows).map(w -> w / 1024).toArray()));
        // Extra diagnosis: warn when a middle window already rises over the previous one (no failure, so GC
        // noise does not cause false alarms)
        for (int i = 1; i < windows.length; i++) {
            if (windows[i] > windows[i - 1]) {
                System.out.printf("[ReloadMemoryStability] note: window %d (%d KB) > window %d (%d KB)%n",
                        i, windows[i] / 1024, i - 1, windows[i - 1] / 1024);
            }
        }
    }

    private static ScriptPropertyRegistry propertyRegistry() {
        var impl = new ScriptPropertyRegistry.Impl();
        impl.register(ScriptProperty.AFTER);
        impl.register(ScriptProperty.MODLOADED);
        impl.register(ScriptProperty.DISABLE);
        impl.register(ScriptProperty.PRIORITY);
        impl.freeze();
        return impl;
    }

    private static long usedHeapAfterGc() throws InterruptedException {
        for (int i = 0; i < 3; i++) {
            System.gc();
            Thread.sleep(50);
        }
        Runtime rt = Runtime.getRuntime();
        return rt.totalMemory() - rt.freeMemory();
    }
}
