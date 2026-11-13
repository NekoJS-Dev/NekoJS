package com.tkisor.nekojs.core.dynamic.facade;

import com.tkisor.nekojs.api.JSTypeAdapter;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.catalog.ManualDeclarationCatalogEntry;
import com.tkisor.nekojs.api.catalog.TypeDocCatalogEntry;
import com.tkisor.nekojs.api.data.Binding;
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
import com.tkisor.nekojs.core.state.GlobalStateStores;
import com.tkisor.nekojs.script.ScriptEnvironmentFactory;
import com.tkisor.nekojs.script.ScriptManager;
import com.tkisor.nekojs.script.ScriptTypeEnv;
import com.tkisor.nekojs.script.prop.ScriptProperty;
import com.tkisor.nekojs.script.prop.ScriptPropertyRegistry;
import graal.graalvm.polyglot.Engine;
import graal.graalvm.polyglot.Value;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Shared facade test harness: a real {@link ScriptManager} with transactional reload, real GraalJS,
 * the production {@link EventGroupJS} bindings (the DynamicRegistryEvents group), and the candidate
 * domain collector wired into {@code ScriptManager}. It assembles the harness the same way
 * {@code ScriptReloadGenerationTest} does.
 */
final class FacadeTestHarness implements AutoCloseable {

    final Engine engine = Engine.newBuilder().build();
    final FacadeBridge bridge = new FacadeBridge();
    final DynamicRegistryFacadeRuntime facade = new DynamicRegistryFacadeRuntime();
    final ScriptManager manager;
    final ScriptType scriptType;

    FacadeTestHarness(ScriptType scriptType) {
        this(scriptType, java.util.List.of());
    }

    /**
     * @param extraCollectors additional candidate domain collectors attached to the manager beside the
     *                        facade; tests that attribute collector failures inject a stub here, and
     *                        production injects them from the root
     */
    FacadeTestHarness(ScriptType scriptType,
            java.util.List<com.tkisor.nekojs.core.lifecycle.CandidateDomainCollector> extraCollectors) {
        // The same test sandbox shape as ScriptReloadGenerationTest.
        SandboxConfig config = new SandboxConfig(false, false, false, false, true, true, false, true);
        NekoJSPaths paths = NekoJSPaths.get();
        this.scriptType = scriptType;
        StubRuntime pluginRuntime = new StubRuntime();
        DefaultErrorTracker tracker = new DefaultErrorTracker(paths, config);
        ScriptCompilerRegistry compilers = ScriptCompilerRegistry.createRuntimeRegistry();
        NekoCoreContext core = new NekoCoreContext(engine, config, new ClassFilter(config), tracker);
        com.tkisor.nekojs.core.module.NekoModulePipelineCache cache =
                com.tkisor.nekojs.testfixture.NekoModuleTestFixtures.newCache(paths, compilers, config);
        NekoSandboxFactory sandboxFactory = new NekoSandboxFactory(core, paths, compilers, pluginRuntime, cache);
        ScriptEnvironmentFactory environmentFactory =
                new ScriptEnvironmentFactory(bridge, pluginRuntime, sandboxFactory, new GlobalStateStores());
        java.util.List<com.tkisor.nekojs.core.lifecycle.CandidateDomainCollector> collectors =
                new java.util.ArrayList<>();
        collectors.add(facade);
        collectors.addAll(extraCollectors);
        // Collectors are injected through the constructor, the root assembly path: there is no process-level
        // static registry any more.
        this.manager = new ScriptManager(scriptType, bridge, pluginRuntime,
                newPropertyRegistry(), tracker, paths, config, environmentFactory, collectors, cache);
    }

    void writeScript(String fileName, String source) throws Exception {
        Path dir = ScriptTypeEnv.scriptsDir(scriptType);
        if (dir == null) {
            throw new IllegalStateException("no scripts dir for " + scriptType);
        }
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(fileName), source);
    }

    @Override
    public void close() {
        manager.close();
        engine.close();
    }

    /** Bridge that binds the DynamicRegistryEvents group, with the production EventGroupJS binding. */
    static final class FacadeBridge implements ScriptEventBridge {
        @Override
        public void bindEvents(Value bindings, ScriptType type) {
            if (type == ScriptType.SERVER) {
                bindings.putMember("DynamicRegistryEvents",
                        new EventGroupJS(DynamicRegistryEvents.GROUP, ScriptType.SERVER));
            }
        }

        @Override
        public void clearListeners(ScriptType type) {
            DynamicRegistryEvents.GROUP.clearListeners(type);
        }
    }

    private static final class StubRuntime implements IPluginRuntime {
        @Override
        public Map<String, Binding> bindings(ScriptType type) {
            return Map.of();
        }

        @Override
        public Map<String, EventGroup> eventGroups() {
            return Map.of("DynamicRegistryEvents", DynamicRegistryEvents.GROUP);
        }

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
