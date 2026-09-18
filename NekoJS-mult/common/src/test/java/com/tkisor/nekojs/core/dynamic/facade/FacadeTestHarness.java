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
 * 票 16 facade 测试共用 harness：真实 {@link ScriptManager}（事务式 reload）+ 真实 GraalJS
 * + 生产同款 {@link EventGroupJS} 绑定（DynamicRegistryEvents 组）+ 候选域收集器挂入
 * {@code ScriptManager}。与 {@code ScriptReloadGenerationTest} 的 harness 同一装配形状。
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
     * @param extraCollectors 与 facade 并列挂进 manager 的额外候选域收集器
     *                        （收集器崩坏归因类用例注入替身；生产由 root 装配注入）
     */
    FacadeTestHarness(ScriptType scriptType,
            java.util.List<com.tkisor.nekojs.core.lifecycle.CandidateDomainCollector> extraCollectors) {
        // 与 ScriptReloadGenerationTest 相同的测试沙盒形状
        SandboxConfig config = new SandboxConfig(false, false, false, false, true, true, false, true, 5,
                100_000L, 0);
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
        // 收集器经构造器（root 装配路径）注入：票 39/16 统一接缝后不再有进程级静态注册表
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

    /** 绑定 DynamicRegistryEvents 组的 bridge（生产同款 EventGroupJS 绑定）。 */
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
