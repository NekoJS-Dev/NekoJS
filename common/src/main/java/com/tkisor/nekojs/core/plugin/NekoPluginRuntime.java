package com.tkisor.nekojs.core.plugin;

import com.tkisor.nekojs.api.JSTypeAdapter;
import com.tkisor.nekojs.api.NekoJSPlugin;
import com.tkisor.nekojs.api.catalog.ManualDeclarationCatalogEntry;
import com.tkisor.nekojs.api.catalog.TypeDocCatalogEntry;
import com.tkisor.nekojs.core.compiler.ScriptCompilerRegistry;
import com.tkisor.nekojs.api.data.Binding;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.api.event.ManagedCallbackSchemaRegistry;
import com.tkisor.nekojs.api.plugin.IPluginRuntime;
import com.tkisor.nekojs.api.plugin.NekoRuntimeAccess;
import com.tkisor.nekojs.api.plugin.OwnedPlugin;
import com.tkisor.nekojs.api.recipe.RecipeLifecycleContext;
import com.tkisor.nekojs.api.recipe.RecipeNamespaceEntry;
import com.tkisor.nekojs.api.recipe.definition.RecipeTypeDefinition;
import com.tkisor.nekojs.api.recipe.definition.RecipeTypeDefinitionRegistry;
import com.tkisor.nekojs.api.recipe.definition.RecipeTypeDefinitionStorage;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.surface.ApiEnvironmentSnapshot;
import com.tkisor.nekojs.api.surface.ApiRuntimeProvider;
import com.tkisor.nekojs.api.surface.ApiRuntimeView;
import com.tkisor.nekojs.api.surface.ApiSurfaceSnapshot;
import com.tkisor.nekojs.api.surface.EnvironmentKey;
import com.tkisor.nekojs.api.surface.EnvironmentKeyFactory;
import com.tkisor.nekojs.api.surface.ApiSymbolId;
import com.tkisor.nekojs.api.contract.NormativeApiContract;
import com.tkisor.nekojs.api.contract.VerifiedApiContract;
import com.tkisor.nekojs.api.contract.VerifiedContractSet;
import com.tkisor.nekojs.core.api.CoreManagedApiBootstrap;
import com.tkisor.nekojs.core.api.FrozenApiRegistrySet;
import com.tkisor.nekojs.platform.Platform;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class NekoPluginRuntime implements IPluginRuntime {
    private static NekoPluginRuntime current;

    private final ScriptCompilerRegistry scriptCompilers;
    private final Map<ScriptType, Map<String, Binding>> bindings;
    private final List<JSTypeAdapter<?>> adapters;
    private final Map<String, EventGroup> eventGroups;
    private final List<TypeDocCatalogEntry> typeDocs;
    private final List<ManualDeclarationCatalogEntry> manualDeclarations;
    private final Map<String, String> nodeModules;
    private final Map<String, RecipeNamespaceEntry> recipeNamespaces;
    private final Map<String, Map<String, RecipeTypeDefinition>> recipeSchemaOverrides;
    private final List<Consumer<RecipeLifecycleContext>> beforeRecipeLoadingHooks;
    private final List<Consumer<RecipeLifecycleContext>> afterRecipesHooks;
    private final List<Runnable> initHooks;
    private final List<Runnable> initStartupHooks;
    private final List<Runnable> afterInitHooks;
    private final List<Consumer<ScriptType>> beforeScriptsLoadedHooks;
    private final List<Consumer<ScriptType>> afterScriptsLoadedHooks;
    private final ApiRuntimeProvider apiRuntimeProvider;
    private final Map<ApiSymbolId, Object> managedApiImplementations;

    NekoPluginRuntime(ScriptCompilerRegistry scriptCompilers,
                      Map<ScriptType, Map<String, Binding>> bindings,
                      List<JSTypeAdapter<?>> adapters,
                      Map<String, EventGroup> eventGroups,
                      List<TypeDocCatalogEntry> typeDocs,
                      List<ManualDeclarationCatalogEntry> manualDeclarations,
                      Map<String, String> nodeModules,
                      Map<String, RecipeNamespaceEntry> recipeNamespaces,
                      Map<String, Map<String, RecipeTypeDefinition>> recipeSchemaOverrides,
                      List<Consumer<RecipeLifecycleContext>> beforeRecipeLoadingHooks,
                      List<Consumer<RecipeLifecycleContext>> afterRecipesHooks,
                      List<Runnable> initHooks,
                      List<Runnable> initStartupHooks,
                      List<Runnable> afterInitHooks,
                      List<Consumer<ScriptType>> beforeScriptsLoadedHooks,
                      List<Consumer<ScriptType>> afterScriptsLoadedHooks,
                       ApiRuntimeProvider apiRuntimeProvider,
                       Map<ApiSymbolId, Object> managedApiImplementations) {
        this.scriptCompilers = scriptCompilers;
        this.bindings = bindings;
        this.adapters = adapters;
        this.eventGroups = eventGroups;
        this.typeDocs = typeDocs;
        this.manualDeclarations = manualDeclarations;
        this.nodeModules = nodeModules;
        this.recipeNamespaces = recipeNamespaces;
        this.recipeSchemaOverrides = recipeSchemaOverrides;
        this.beforeRecipeLoadingHooks = beforeRecipeLoadingHooks;
        this.afterRecipesHooks = afterRecipesHooks;
        this.initHooks = initHooks;
        this.initStartupHooks = initStartupHooks;
        this.afterInitHooks = afterInitHooks;
        this.beforeScriptsLoadedHooks = beforeScriptsLoadedHooks;
        this.afterScriptsLoadedHooks = afterScriptsLoadedHooks;
        this.apiRuntimeProvider = apiRuntimeProvider;
        this.managedApiImplementations = Map.copyOf(managedApiImplementations);
        publishRecipeSchemaOverrides();
    }

    private void publishRecipeSchemaOverrides() {
        if (recipeSchemaOverrides.isEmpty()) return;
        RecipeTypeDefinitionRegistry.Builder builder = RecipeTypeDefinitionRegistry.builder();
        for (var nsEntry : recipeSchemaOverrides.entrySet()) {
            for (var typeEntry : nsEntry.getValue().entrySet()) {
                builder.add(typeEntry.getValue());
            }
        }
        RecipeTypeDefinitionStorage.setPluginOverrides(builder.build());
    }

    /**
     * Legacy bootstrap 入口：无生产调用方（所有平台经 {@link #bootstrapOwned}），
     * 仅为嵌入场景/兼容测试保留。API freeze 后计划移除，新代码请用 {@link #bootstrapOwned}。
     */
    @Deprecated
    public static NekoPluginRuntime bootstrap(List<NekoJSPlugin> plugins, com.tkisor.nekojs.script.prop.ScriptPropertyRegistry scriptProperties) {
        NekoPluginRuntime runtime = NekoPluginBootstrap.bootstrap(plugins, scriptProperties);
        current = runtime;
        NekoRuntimeAccess.set(runtime);
        ScriptCompilerRegistry.useRuntime(runtime.scriptCompilers());
        return runtime;
    }

    public static NekoPluginRuntime bootstrapOwned(
            List<OwnedPlugin> ownedPlugins,
            com.tkisor.nekojs.script.prop.ScriptPropertyRegistry scriptProperties) {
        java.net.URI codeSource;
        try {
            codeSource = NekoPluginRuntime.class.getProtectionDomain().getCodeSource().getLocation().toURI();
        } catch (java.net.URISyntaxException e) {
            throw new IllegalStateException("Failed to resolve NekoJS code source URI", e);
        }
        CoreManagedApiBootstrap.CoreManagedApi core = CoreManagedApiBootstrap.load(Platform.instance(), codeSource);
        NekoPluginRuntime runtime = NekoPluginBootstrap.bootstrapOwned(
                ownedPlugins,
                scriptProperties,
                core.contracts(),
                List.of(core.contributions()),
                core.globalImplementations());
        publish(runtime);
        return runtime;
    }

    public static NekoPluginRuntime bootstrapOwned(
            List<OwnedPlugin> ownedPlugins,
            com.tkisor.nekojs.script.prop.ScriptPropertyRegistry scriptProperties,
            VerifiedContractSet contracts) {
        NekoPluginRuntime runtime = NekoPluginBootstrap.bootstrapOwned(ownedPlugins, scriptProperties, contracts);
        publish(runtime);
        return runtime;
    }

    private static void publish(NekoPluginRuntime runtime) {
        current = runtime;
        NekoRuntimeAccess.set(runtime);
        ScriptCompilerRegistry.useRuntime(runtime.scriptCompilers());
        installManagedCallbackSchemas(runtime);
    }

    public static NekoPluginRuntime current() {
        if (current == null) {
            throw new IllegalStateException("NekoPluginRuntime has not been bootstrapped yet");
        }
        return current;
    }

    private static void installManagedCallbackSchemas(NekoPluginRuntime runtime) {
        if (runtime.apiRuntimeProvider == null) return;
        Map<ScriptType, ApiSurfaceSnapshot> snapshots = new java.util.HashMap<>();
        for (ScriptType type : ScriptType.values()) {
            EnvironmentKey key = EnvironmentKeyFactory.current(type);
            ApiRuntimeView view = runtime.apiRuntimeProvider.view(key);
            if (view == null) continue;
            ApiEnvironmentSnapshot envSnap = view.environmentSnapshot();
            if (envSnap != null && envSnap.surfaceSnapshot() != null) {
                snapshots.put(type, envSnap.surfaceSnapshot());
            }
        }
        if (!snapshots.isEmpty()) {
            ManagedCallbackSchemaRegistry.install(snapshots);
        }
        // 事件回调 schema：从运行时 EventGroup 反射派生（替代从 portable-core JSON events 读取）。
        // ContractEvent 的 payload 字段从 bus.eventType() 反射，字段名跨平台由 mixin 注入的
        // neko$ 别名统一（如 BlockEventExtension.neko$getLevel）。平台反射仅作补充（见 EventCallbackSourceValidator）。
        List<NormativeApiContract.ContractEvent> contractEvents =
                com.tkisor.nekojs.core.api.EventContractReflector.extractEvents(runtime.eventGroups().values());
        ManagedCallbackSchemaRegistry.installContractEvents(contractEvents);
    }

    public ScriptCompilerRegistry scriptCompilers() {
        return scriptCompilers;
    }

    public Map<String, Binding> bindings(ScriptType type) {
        return bindings.getOrDefault(type, Map.of());
    }

    public List<JSTypeAdapter<?>> adapters() {
        return adapters;
    }

    public Map<String, EventGroup> eventGroups() {
        return eventGroups;
    }

    public List<TypeDocCatalogEntry> typeDocs() {
        return typeDocs;
    }

    public List<ManualDeclarationCatalogEntry> manualDeclarations() {
        return manualDeclarations;
    }

    public Map<String, String> nodeModules() {
        return nodeModules;
    }

    /**
     * 插件经 {@code registerRecipeSchemas} 注册（或覆盖）的配方 schema 快照
     * （namespace → type → definition，已冻结）。此前只能经
     * {@code RecipeTypeDefinitionStorage} 的合并视图间接观测，补一个直接访问器。
     */
    public Map<String, Map<String, RecipeTypeDefinition>> recipeSchemaOverrides() {
        return recipeSchemaOverrides;
    }

    public Map<String, RecipeNamespaceEntry> recipeNamespaces() {
        return recipeNamespaces;
    }

    public List<Consumer<RecipeLifecycleContext>> beforeRecipeLoadingHooks() {
        return beforeRecipeLoadingHooks;
    }

    public List<Consumer<RecipeLifecycleContext>> afterRecipesHooks() {
        return afterRecipesHooks;
    }

    public void beforeRecipeLoading(RecipeLifecycleContext context) {
        runRecipeHooks(beforeRecipeLoadingHooks, context);
    }

    public void afterRecipes(RecipeLifecycleContext context) {
        runRecipeHooks(afterRecipesHooks, context);
    }

    private void runRecipeHooks(List<Consumer<RecipeLifecycleContext>> hooks, RecipeLifecycleContext context) {
        for (Consumer<RecipeLifecycleContext> hook : hooks) {
            try {
                hook.accept(context);
            } catch (Exception e) {
                ScriptType.SERVER.logger().error("Recipe lifecycle hook failed", e);
            }
        }
    }

    @Override
    public void fireInit() {
        runRunnableHooks(initHooks, ScriptType.STARTUP, "init");
    }

    @Override
    public void fireInitStartup() {
        runRunnableHooks(initStartupHooks, ScriptType.STARTUP, "initStartup");
    }

    @Override
    public void fireAfterInit() {
        runRunnableHooks(afterInitHooks, ScriptType.STARTUP, "afterInit");
    }

    @Override
    public void fireBeforeScriptsLoaded(ScriptType type) {
        runScriptTypeHooks(beforeScriptsLoadedHooks, type, "beforeScriptsLoaded");
    }

    @Override
    public void fireAfterScriptsLoaded(ScriptType type) {
        runScriptTypeHooks(afterScriptsLoadedHooks, type, "afterScriptsLoaded");
    }

    @Override
    public ApiRuntimeView apiRuntime(EnvironmentKey environment) {
        if (apiRuntimeProvider == null) {
            return null;
        }
        return apiRuntimeProvider.view(environment);
    }

    @Override
    public Object managedApiImplementation(ApiSymbolId globalId) {
        return managedApiImplementations.get(globalId);
    }

    public ApiRuntimeProvider apiRuntimeProvider() {
        return apiRuntimeProvider;
    }

    private void runRunnableHooks(List<Runnable> hooks, ScriptType loggerType, String name) {
        for (Runnable hook : hooks) {
            try {
                hook.run();
            } catch (Exception e) {
                loggerType.logger().error("Lifecycle " + name + " hook failed", e);
            }
        }
    }

    private void runScriptTypeHooks(List<Consumer<ScriptType>> hooks, ScriptType type, String name) {
        for (Consumer<ScriptType> hook : hooks) {
            try {
                hook.accept(type);
            } catch (Exception e) {
                type.logger().error("Lifecycle " + name + " hook failed for " + type.name(), e);
            }
        }
    }

}
