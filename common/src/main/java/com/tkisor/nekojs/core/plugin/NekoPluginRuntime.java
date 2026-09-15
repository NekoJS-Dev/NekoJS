package com.tkisor.nekojs.core.plugin;

import com.tkisor.nekojs.api.JSTypeAdapter;
import com.tkisor.nekojs.api.NekoJSPlugin;
import com.tkisor.nekojs.api.catalog.ManualDeclarationCatalogEntry;
import com.tkisor.nekojs.api.catalog.RegistryBuilderSurfaceEntry;
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
import com.tkisor.nekojs.api.surface.ApiSymbolId;
import com.tkisor.nekojs.api.contract.NormativeApiContract;
import com.tkisor.nekojs.api.contract.VerifiedContractSet;
import com.tkisor.nekojs.core.api.CoreManagedApiBootstrap;
import com.tkisor.nekojs.core.api.FrozenApiRegistrySet;
import com.tkisor.nekojs.platform.Platform;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * bootstrap 的结果容器：持有全部扩展点产物（{@code pointId → finisher 产物}），
 * 公开访问器均为产物的类型化派生视图。
 *
 * <p>runtime 不再参与收集（收集由 {@link NekoPluginBootstrap} 的点优先执行序完成），
 * 仅消费不可变产物；除少量装配副作用（如 {@code publishRecipeSchemaOverrides}）外构造后只读。
 */
public final class NekoPluginRuntime implements IPluginRuntime {
    private static NekoPluginRuntime current;

    private final Map<String, Object> extensionProducts;
    private final ApiRuntimeProvider apiRuntimeProvider;
    private final Map<ApiSymbolId, Object> managedApiImplementations;

    private final ScriptCompilerRegistry scriptCompilers;
    private final Map<ScriptType, Map<String, Binding>> bindings;
    private final List<JSTypeAdapter<?>> adapters;
    private final Map<String, EventGroup> eventGroups;
    private final List<TypeDocCatalogEntry> typeDocs;
    private final List<ManualDeclarationCatalogEntry> manualDeclarations;
    private final List<RegistryBuilderSurfaceEntry> registryBuilderSurfaces;
    private final Map<String, String> nodeModules;
    private final Map<String, RecipeNamespaceEntry> recipeNamespaces;
    private final Map<String, Map<String, RecipeTypeDefinition>> recipeSchemaOverrides;
    private final RecipeLifecyclePoint.RecipeLifecycleHooks recipeLifecycleHooks;
    private final LifecyclePoint.LifecycleHooks lifecycleHooks;

    NekoPluginRuntime(Map<String, Object> extensionProducts,
                      ApiRuntimeProvider apiRuntimeProvider,
                      Map<ApiSymbolId, Object> managedApiImplementations) {
        this.extensionProducts = Collections.unmodifiableMap(new LinkedHashMap<>(extensionProducts));
        this.apiRuntimeProvider = apiRuntimeProvider;
        this.managedApiImplementations = Map.copyOf(managedApiImplementations);
        this.scriptCompilers = product(ScriptCompilersPoint.ID);
        this.bindings = product(BindingsPoint.ID);
        this.adapters = product(AdaptersPoint.ID);
        this.eventGroups = EventsPoint.mergedEventGroups(extensionProducts);
        this.typeDocs = mergedTypeDocs();
        this.manualDeclarations = mergedManualDeclarations();
        this.registryBuilderSurfaces = mergedRegistryBuilderSurfaces();
        this.nodeModules = product(NodeModulesPoint.ID);
        this.recipeNamespaces = product(RecipeNamespacesPoint.ID);
        this.recipeSchemaOverrides = product(RecipeSchemasPoint.ID);
        this.recipeLifecycleHooks = product(RecipeLifecyclePoint.ID);
        this.lifecycleHooks = product(LifecyclePoint.ID);
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
            EnvironmentKey key = EnvironmentKey.current(type);
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

    @Override
    public List<RegistryBuilderSurfaceEntry> registryBuilderSurfaces() {
        return registryBuilderSurfaces;
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
        return recipeLifecycleHooks.beforeRecipeLoading();
    }

    public List<Consumer<RecipeLifecycleContext>> afterRecipesHooks() {
        return recipeLifecycleHooks.afterRecipes();
    }

    public void beforeRecipeLoading(RecipeLifecycleContext context) {
        runRecipeHooks(beforeRecipeLoadingHooks(), context);
    }

    public void afterRecipes(RecipeLifecycleContext context) {
        runRecipeHooks(afterRecipesHooks(), context);
    }

    private void runRecipeHooks(List<Consumer<RecipeLifecycleContext>> hooks, RecipeLifecycleContext context) {
        for (Consumer<RecipeLifecycleContext> hook : hooks) {
            try {
                hook.accept(context);
            } catch (Exception e) {
                com.tkisor.nekojs.script.ScriptTypeEnv.logger(ScriptType.SERVER).error("Recipe lifecycle hook failed", e);
            }
        }
    }

    @Override
    public void fireInit() {
        runRunnableHooks(lifecycleHooks.init(), ScriptType.STARTUP, "init");
    }

    @Override
    public void fireInitStartup() {
        runRunnableHooks(lifecycleHooks.initStartup(), ScriptType.STARTUP, "initStartup");
    }

    @Override
    public void fireAfterInit() {
        runRunnableHooks(lifecycleHooks.afterInit(), ScriptType.STARTUP, "afterInit");
    }

    @Override
    public void fireBeforeScriptsLoaded(ScriptType type) {
        runScriptTypeHooks(lifecycleHooks.beforeScriptsLoaded(), type, "beforeScriptsLoaded");
    }

    @Override
    public void fireAfterScriptsLoaded(ScriptType type) {
        runScriptTypeHooks(lifecycleHooks.afterScriptsLoaded(), type, "afterScriptsLoaded");
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

    /** 全部扩展点产物（pointId → finisher 产物）的只读视图，自定义扩展点的产物也在其中。 */
    public Map<String, Object> extensionProducts() {
        return extensionProducts;
    }

    /**
     * 按扩展点 id 取产物（含自定义扩展点）。该点未执行、被环境跳过或产物为 null 时返回
     * {@code null}；产物类型与期望不符时抛 {@link IllegalStateException}。
     */
    public <R> R extensionProduct(String pointId, Class<R> type) {
        Object product = extensionProducts.get(pointId);
        if (product == null) {
            return null;
        }
        if (!type.isInstance(product)) {
            throw new IllegalStateException("Plugin extension point '" + pointId
                    + "' product type mismatch: expected " + type.getName()
                    + " but was " + product.getClass().getName());
        }
        return type.cast(product);
    }

    /** type_docs 与 node_type_docs 两点产物的合并：按 priority 稳定排序（同优先级 type_docs 在前）。 */
    private List<TypeDocCatalogEntry> mergedTypeDocs() {
        List<TypeDocCatalogEntry> merged = new ArrayList<>(
                typeDocsSnapshot(TypeDocsPoint.ID).docs());
        merged.addAll(typeDocsSnapshot(NodeTypeDocsPoint.ID).docs());
        merged.sort(Comparator.comparingInt(TypeDocCatalogEntry::priority));
        return List.copyOf(merged);
    }

    private List<ManualDeclarationCatalogEntry> mergedManualDeclarations() {
        List<ManualDeclarationCatalogEntry> merged = new ArrayList<>(
                typeDocsSnapshot(TypeDocsPoint.ID).manualDeclarations());
        merged.addAll(typeDocsSnapshot(NodeTypeDocsPoint.ID).manualDeclarations());
        merged.sort(Comparator.comparingInt(ManualDeclarationCatalogEntry::priority));
        return List.copyOf(merged);
    }

    /** type_docs 与 node_type_docs 两点 builder 契约条目的合并（ticket 15，与手写声明同序合并）。 */
    private List<RegistryBuilderSurfaceEntry> mergedRegistryBuilderSurfaces() {
        List<RegistryBuilderSurfaceEntry> merged = new ArrayList<>(
                typeDocsSnapshot(TypeDocsPoint.ID).registryBuilderSurfaces());
        merged.addAll(typeDocsSnapshot(NodeTypeDocsPoint.ID).registryBuilderSurfaces());
        merged.sort(Comparator.comparing(RegistryBuilderSurfaceEntry::builderName)
                .thenComparing(RegistryBuilderSurfaceEntry::typeName));
        return List.copyOf(merged);
    }

    private TypeDocsPoint.TypeDocsSnapshot typeDocsSnapshot(String pointId) {
        return product(pointId);
    }

    @SuppressWarnings("unchecked")
    private <T> T product(String pointId) {
        Object product = extensionProducts.get(pointId);
        if (product == null) {
            throw new IllegalStateException("Extension point '" + pointId + "' produced no product for this bootstrap");
        }
        return (T) product;
    }

    private void runRunnableHooks(List<Runnable> hooks, ScriptType loggerType, String name) {
        for (Runnable hook : hooks) {
            try {
                hook.run();
            } catch (Exception e) {
                com.tkisor.nekojs.script.ScriptTypeEnv.logger(loggerType).error("Lifecycle " + name + " hook failed", e);
            }
        }
    }

    private void runScriptTypeHooks(List<Consumer<ScriptType>> hooks, ScriptType type, String name) {
        for (Consumer<ScriptType> hook : hooks) {
            try {
                hook.accept(type);
            } catch (Exception e) {
                com.tkisor.nekojs.script.ScriptTypeEnv.logger(type).error("Lifecycle " + name + " hook failed for " + type.name(), e);
            }
        }
    }

}
