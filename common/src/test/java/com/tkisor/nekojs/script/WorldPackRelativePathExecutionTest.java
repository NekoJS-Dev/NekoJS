package com.tkisor.nekojs.script;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.data.Binding;
import com.tkisor.nekojs.api.plugin.IPluginRuntime;
import com.tkisor.nekojs.core.NekoCoreContext;
import com.tkisor.nekojs.core.NekoSandboxFactory;
import com.tkisor.nekojs.core.ScriptEventBridge;
import com.tkisor.nekojs.core.compiler.ScriptCompilerRegistry;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.error.DefaultErrorTracker;
import com.tkisor.nekojs.core.fs.ClassFilter;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.module.NekoModulePipelineCache;
import com.tkisor.nekojs.core.pack.ScriptPack;
import com.tkisor.nekojs.core.pack.ScriptPackRegistry;
import com.tkisor.nekojs.script.prop.ScriptPropertyRegistry;
import com.tkisor.nekojs.testfixture.NekoModuleTestFixtures;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import graal.graalvm.polyglot.Engine;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WORLD pack relative-path regression (ticket 03 §3-1 real-code defect, ticket 07 G4
 * leftover, picked up by ticket 19): the world directory handed in by the platform may be
 * relative (a Windows dedicated server was observed passing {@code .\world\.}) while the
 * nekojs root is always absolute. If pack script paths stay relative,
 * {@code paths.root().relativize(script.path)} in {@code ScriptExecutor} mixes absolute and
 * relative inputs and throws IAE — after WORLD pack activation, the SERVER reload fails
 * every script of that pack (errors land in the panel, scripts get disabled): "activated
 * but never ran".
 *
 * <p>Fix semantics: {@link ScriptPackRegistry#activateWorldPacks} normalizes the world
 * directory to an absolute path before scanning, so pack script paths are already absolute
 * when they enter ScriptContainer.
 *
 * <p>Test geometry: the shared test gameDir lives on the system temp drive, which may not be
 * the same drive as the Gradle CWD (the module directory), so no relative path from CWD can
 * reach it. This test therefore temporarily swaps the {@code NekoJSPaths} singleton for a
 * gameDir built under {@code build/} on the CWD's drive (making a relative world dir
 * reachable) and restores the original instance and global pack scan afterwards. This is a
 * test-only reflection seam (precedent: same-tree tests reflecting Platform.INSTANCE).
 */
class WorldPackRelativePathExecutionTest {

    @BeforeAll
    static void initPlatform() {
        TestPlatformInit.ensureInitialized();
    }

    @Test
    void worldPackActivatedFromRelativeDirExecutesAfterReload() throws Exception {
        Path cwd = Path.of("").toAbsolutePath();
        Path gameDir = cwd.resolve("build").resolve("nekojs-g4-worldpack-gamedir");
        deleteRecursively(gameDir);
        Path worldDir = gameDir.resolve("world");
        Path packScripts = worldDir.resolve(ScriptPackRegistry.WORLD_PACKS_DIR)
                .resolve("g4demo").resolve("server_scripts");
        Files.createDirectories(packScripts);
        Files.writeString(packScripts.getParent().resolve("manifest.json"), "{\"id\": \"g4demo\"}");
        Files.writeString(packScripts.resolve("w.js"), "TestRecorder.record('g4-world-pack-ok');\n");

        // World dir in relative form: same shape the production platform entry (server.getWorldPath) was observed handing in
        Path relativeWorldDir = cwd.relativize(worldDir);
        assertFalse(relativeWorldDir.isAbsolute(), "test setup must hand the registry a relative world dir");

        NekoJSPaths originalPaths = NekoJSPaths.get();
        NekoJSPaths testPaths = NekoJSPaths.fromGameDir(gameDir);
        swapPathsInstance(testPaths);
        Recorder recorder = new Recorder();
        Engine engine = Engine.newBuilder().build();
        ScriptManager manager = null;
        try {
            ScriptPackRegistry.get().refreshGlobalPacks();
            ScriptPackRegistry.get().activateWorldPacks(relativeWorldDir);

            List<ScriptPack> worldPacks = ScriptPackRegistry.get().worldPacks();
            assertEquals(1, worldPacks.size(), "the relative world dir must still be scannable");
            assertTrue(worldPacks.get(0).root().isAbsolute(),
                    "activation must normalize pack roots to absolute paths before they reach ScriptContainer");

            manager = newManager(testPaths, recorder, engine);
            manager.discoverScripts();
            manager.loadScripts();
            assertEquals("g4-world-pack-ok", recorder.value(),
                    "world pack scripts must execute after the activation reload");
        } finally {
            ScriptPackRegistry.get().deactivateWorldPacks();
            if (manager != null) {
                manager.close();
            }
            engine.close();
            swapPathsInstance(originalPaths);
            ScriptPackRegistry.get().refreshGlobalPacks();
            deleteRecursively(gameDir);
        }
    }

    /* ================= test assembly ================= */

    /** Script-side reporting channel (global binding {@code TestRecorder}). */
    public static final class Recorder {
        private volatile String value;

        public void record(String value) {
            this.value = value;
        }

        String value() {
            return value;
        }
    }

    private static ScriptManager newManager(NekoJSPaths paths, Recorder recorder, Engine engine) {
        SandboxConfig config = SandboxConfig.defaultConfig();
        DefaultErrorTracker tracker = new DefaultErrorTracker(paths, config);
        StubPluginRuntime pluginRuntime = new StubPluginRuntime(recorder);
        ScriptCompilerRegistry compilers = ScriptCompilerRegistry.createRuntimeRegistry();
        NekoCoreContext core = new NekoCoreContext(engine, config, new ClassFilter(config), tracker);
        NekoModulePipelineCache cache = NekoModuleTestFixtures.newCache(paths, compilers, config);
        NekoSandboxFactory sandboxFactory = new NekoSandboxFactory(core, paths, compilers, pluginRuntime, cache);
        ScriptEnvironmentFactory environmentFactory = new ScriptEnvironmentFactory(
                ScriptEventBridge.EMPTY, pluginRuntime, sandboxFactory,
                new com.tkisor.nekojs.core.state.GlobalStateStores());
        return new ScriptManager(ScriptType.SERVER, ScriptEventBridge.EMPTY, pluginRuntime,
                newPropertyRegistry(), tracker, paths, config, environmentFactory,
                List.of(), cache);
    }

    private static ScriptPropertyRegistry newPropertyRegistry() {
        ScriptPropertyRegistry.Impl impl = new ScriptPropertyRegistry.Impl();
        impl.register(com.tkisor.nekojs.script.prop.ScriptProperty.AFTER);
        impl.register(com.tkisor.nekojs.script.prop.ScriptProperty.MODLOADED);
        impl.register(com.tkisor.nekojs.script.prop.ScriptProperty.DISABLE);
        impl.register(com.tkisor.nekojs.script.prop.ScriptProperty.PRIORITY);
        impl.freeze();
        return impl;
    }

    private static final class StubPluginRuntime implements IPluginRuntime {
        private final Recorder recorder;

        StubPluginRuntime(Recorder recorder) {
            this.recorder = recorder;
        }

        @Override public Map<String, Binding> bindings(ScriptType type) {
            return Map.of("TestRecorder", new Binding() {
                @Override public String name() { return "TestRecorder"; }
                @Override public Object value() { return recorder; }
                @Override public void close(ScriptType closedType) {}
            });
        }

        @Override public Map<String, com.tkisor.nekojs.api.event.EventGroup> eventGroups() { return Map.of(); }
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

    /** Temporarily swaps the {@code NekoJSPaths} singleton (this test JVM only, restored afterwards). */
    private static void swapPathsInstance(NekoJSPaths replacement) throws Exception {
        Field instance = NekoJSPaths.class.getDeclaredField("INSTANCE");
        instance.setAccessible(true);
        instance.set(null, replacement);
    }

    private static void deleteRecursively(Path dir) {
        if (!Files.exists(dir)) return;
        try (Stream<Path> stream = Files.walk(dir)) {
            stream.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (Exception ignored) {
                    // A log FileAppender may hold the handle; leftovers sit under build/ and are cleaned by later builds
                }
            });
        } catch (Exception ignored) {
            // Same as above: best-effort cleanup
        }
    }
}
