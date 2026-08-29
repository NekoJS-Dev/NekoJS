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
import com.tkisor.nekojs.script.prop.ScriptProperty;
import com.tkisor.nekojs.script.prop.ScriptPropertyRegistry;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import graal.graalvm.polyglot.Engine;
import graal.graalvm.polyglot.Value;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression test for the B3 follow-up: a targeted STARTUP {@code reloadScriptFile}
 * must degrade to a FULL STARTUP reload instead of the partial invalidation/rerun path,
 * because STARTUP registrations are irreversible and re-posting only the affected
 * ScriptEvents listeners would wipe unaffected custom-event listener tokens.
 */
class StartupReloadScriptFileFullReloadTest {

    public static final class TestRecorder {
        private volatile String value;

        public void record(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }

    private static final class StubPluginRuntime implements IPluginRuntime {
        private final TestRecorder recorder;

        StubPluginRuntime(TestRecorder recorder) {
            this.recorder = recorder;
        }

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

    private static final class RecordingEventBridge implements ScriptEventBridge {
        final List<ScriptType> typeOnlyCleared = new CopyOnWriteArrayList<>();
        final List<String> scriptScopedCleared = new CopyOnWriteArrayList<>();

        @Override
        public void bindEvents(Value bindings, ScriptType type) {
        }

        @Override
        public void clearListeners(ScriptType type) {
            typeOnlyCleared.add(type);
        }

        @Override
        public void clearListeners(ScriptType type, String scriptId) {
            scriptScopedCleared.add(type + ":" + scriptId);
        }
    }

    @BeforeAll
    static void initPlatform() {
        TestPlatformInit.ensureInitialized();
    }

    @BeforeEach
    void cleanStartupScriptDir() throws Exception {
        Path startupDir = NekoJSPaths.get().startupScripts();
        Files.createDirectories(startupDir);
        try (var stream = Files.list(startupDir)) {
            for (Path path : stream.toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    @Test
    void startupReloadScriptFilePerformsFullReload() throws Exception {
        NekoJSPaths paths = NekoJSPaths.get();
        Path startupDir = paths.startupScripts();
        Files.createDirectories(startupDir);
        Path entry = startupDir.resolve("entry.js");

        Files.writeString(entry, "TestRecorder.record('v1');\n");

        TestRecorder recorder = new TestRecorder();
        Engine engine = Engine.newBuilder().build();
        RecordingEventBridge bridge = new RecordingEventBridge();
        ScriptManager manager = null;
        try {
            manager = newStartupManager(paths, recorder, engine, bridge);
            manager.discoverScripts();
            manager.loadScripts();
            assertEquals("v1", recorder.value(), "initial full load must execute the startup entry");
            bridge.typeOnlyCleared.clear();
            bridge.scriptScopedCleared.clear();

            Files.writeString(entry, "TestRecorder.record('v2');\n");
            var reloaded = manager.reloadScriptFile("entry.js");

            assertEquals("v2", recorder.value(), "STARTUP file reload must re-execute the startup entry via full reload");
            assertFalse(reloaded.isEmpty(), "reloadScriptFile must return the reloaded startup entry");
            assertTrue(bridge.typeOnlyCleared.contains(ScriptType.STARTUP),
                    "STARTUP file reload must trigger a full STARTUP clear (clearListeners(STARTUP))");
            assertTrue(bridge.scriptScopedCleared.isEmpty(),
                    "STARTUP file reload must NOT use the per-script clear path");
        } finally {
            if (manager != null) {
                manager.close();
            }
            engine.close();
        }
    }

    private static ScriptManager newStartupManager(NekoJSPaths paths, TestRecorder recorder, Engine engine,
                                                   ScriptEventBridge eventBridge) {
        StubPluginRuntime pluginRuntime = new StubPluginRuntime(recorder);
        SandboxConfig config = SandboxConfig.defaultConfig();
        DefaultErrorTracker tracker = new DefaultErrorTracker(paths, config);
        ScriptCompilerRegistry compilers = ScriptCompilerRegistry.createRuntimeRegistry();
        NekoCoreContext core = new NekoCoreContext(engine, config, new ClassFilter(config), tracker);
        NekoSandboxFactory sandboxFactory = new NekoSandboxFactory(core, paths, compilers, pluginRuntime);
        ScriptEnvironmentFactory environmentFactory =
                new ScriptEnvironmentFactory(eventBridge, pluginRuntime, sandboxFactory);
        return new ScriptManager(ScriptType.STARTUP, eventBridge, pluginRuntime,
                newPropertyRegistry(), tracker, paths, config, environmentFactory);
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
}
