package com.tkisor.nekojs.core.plugin;

import com.tkisor.nekojs.api.JSTypeAdapter;
import com.tkisor.nekojs.api.catalog.ManualDeclarationCatalogEntry;
import com.tkisor.nekojs.api.catalog.ClassDeclarationCatalogEntry;
import com.tkisor.nekojs.api.catalog.BeanAliasExclusionCatalogEntry;
import com.tkisor.nekojs.api.catalog.BeanMethodProjectionCatalogEntry;
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
 * Result container of a bootstrap: it holds every extension point product, keyed from pointId
 * to the finisher product, and its public accessors are typed views derived from those
 * products.
 *
 * <p>The runtime no longer collects anything; {@link NekoPluginBootstrap} does that in
 * point-first execution order, and the runtime only consumes the immutable products. Apart
 * from a few assembly side effects (such as {@code publishRecipeSchemaOverrides}) it is
 * read-only after construction.
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
    private final List<ClassDeclarationCatalogEntry> classDeclarations;
    private final List<BeanAliasExclusionCatalogEntry> beanAliasExclusions;
    private final List<BeanMethodProjectionCatalogEntry> beanMethodProjections;
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
        this.classDeclarations = mergedClassDeclarations();
        this.beanAliasExclusions = mergedBeanAliasExclusions();
        this.beanMethodProjections = mergedBeanMethodProjections();
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
        // Event callback schema, derived by reflecting the runtime EventGroups (instead of
        // reading the portable-core JSON events). ContractEvent payload fields come from
        // bus.eventType(); field names are made uniform across platforms by mixin-injected
        // neko$ aliases (for example BlockEventExtension.neko$getLevel). Platform reflection
        // only supplements this (see EventCallbackSourceValidator).
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

    @Override
    public List<com.tkisor.nekojs.api.data.ModuleBinding> moduleBindings(ScriptType type) {
        if (!(bindings instanceof BindingsPoint.Snapshot snapshot)) return List.of();
        return snapshot.modules().stream().filter(entry -> entry.scriptType() == type).toList();
    }

    public List<JSTypeAdapter<?>> adapters() {
        return adapters;
    }

    @Override
    public List<com.tkisor.nekojs.api.event.ModuleEventGroup> moduleEventGroups() {
        return eventGroups instanceof EventsPoint.Snapshot snapshot ? snapshot.modules() : List.of();
    }

    @Override
    public List<EventGroup> lifecycleEventGroups() {
        return eventGroups instanceof EventsPoint.Snapshot snapshot ? snapshot.sources() : List.copyOf(eventGroups.values());
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
     * Snapshot of the recipe schemas that plugins registered or overrode through
     * {@code registerRecipeSchemas}, frozen, keyed by namespace and then by type. The same data
     * is reachable through the merged view of {@code RecipeTypeDefinitionStorage}; this
     * accessor returns it directly.
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

    /** Read-only view of every extension point product (pointId to finisher product), including
     * the products of custom points. */
    public Map<String, Object> extensionProducts() {
        return extensionProducts;
    }

    /**
     * Returns the product for an extension point id, including custom points. Returns
     * {@code null} when the point did not run, was skipped by the environment, or produced null;
     * throws {@link IllegalStateException} when the product type does not match.
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

    /** Merges the type_docs and node_type_docs products: stable sort by priority, so entries of
     * the same priority keep type_docs first. */
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

    private List<ClassDeclarationCatalogEntry> mergedClassDeclarations() {
        List<ClassDeclarationCatalogEntry> merged = new ArrayList<>(
                typeDocsSnapshot(TypeDocsPoint.ID).classDeclarations());
        merged.addAll(typeDocsSnapshot(NodeTypeDocsPoint.ID).classDeclarations());
        return ClassDeclarationCatalogEntry.resolve(merged);
    }

    private List<BeanAliasExclusionCatalogEntry> mergedBeanAliasExclusions() {
        List<BeanAliasExclusionCatalogEntry> merged = new ArrayList<>(
                typeDocsSnapshot(TypeDocsPoint.ID).beanAliasExclusions());
        merged.addAll(typeDocsSnapshot(NodeTypeDocsPoint.ID).beanAliasExclusions());
        return merged.stream().distinct().toList();
    }

    @Override
    public List<BeanAliasExclusionCatalogEntry> beanAliasExclusions() {
        return beanAliasExclusions;
    }

    @Override
    public List<ClassDeclarationCatalogEntry> classDeclarations() {
        return classDeclarations;
    }

    /** Merges the builder contract entries of the type_docs and node_type_docs points, in the same
     * order used for handwritten declarations. */
    private List<BeanMethodProjectionCatalogEntry> mergedBeanMethodProjections() {
        List<BeanMethodProjectionCatalogEntry> merged = new ArrayList<>(
                typeDocsSnapshot(TypeDocsPoint.ID).beanMethodProjections());
        merged.addAll(typeDocsSnapshot(NodeTypeDocsPoint.ID).beanMethodProjections());
        return merged.stream().distinct().toList();
    }

    @Override
    public List<BeanMethodProjectionCatalogEntry> beanMethodProjections() {
        return beanMethodProjections;
    }

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
