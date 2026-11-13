package com.tkisor.nekojs.api.plugin;

import com.tkisor.nekojs.api.JSTypeAdapter;
import com.tkisor.nekojs.api.catalog.ManualDeclarationCatalogEntry;
import com.tkisor.nekojs.api.catalog.ClassDeclarationCatalogEntry;
import com.tkisor.nekojs.api.catalog.BeanAliasExclusionCatalogEntry;
import com.tkisor.nekojs.api.catalog.BeanMethodProjectionCatalogEntry;
import com.tkisor.nekojs.api.catalog.RegistryBuilderSurfaceEntry;
import com.tkisor.nekojs.api.catalog.TypeDocCatalogEntry;
import com.tkisor.nekojs.api.data.Binding;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.api.recipe.RecipeLifecycleContext;
import com.tkisor.nekojs.api.recipe.RecipeNamespaceEntry;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.surface.ApiRuntimeView;
import com.tkisor.nekojs.api.surface.ApiSymbolId;
import com.tkisor.nekojs.api.surface.EnvironmentKey;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Minimal read-only contract that the api layer needs from the plugin runtime.
 * Implemented by {@code NekoPluginRuntime} in core.
 */
public interface IPluginRuntime {

    Map<String, Binding> bindings(ScriptType type);

    /** Module-only registrations; these do not appear in the legacy globals map. */
    default List<com.tkisor.nekojs.api.data.ModuleBinding> moduleBindings(ScriptType type) {
        return List.of();
    }

    Map<String, EventGroup> eventGroups();

    default List<com.tkisor.nekojs.api.event.ModuleEventGroup> moduleEventGroups() { return List.of(); }
    default List<EventGroup> lifecycleEventGroups() { return List.copyOf(eventGroups().values()); }

    List<JSTypeAdapter<?>> adapters();

    List<TypeDocCatalogEntry> typeDocs();

    List<ManualDeclarationCatalogEntry> manualDeclarations();

    /** Returns immutable global class replacements collected during Plugin Runtime bootstrap. */
    default List<ClassDeclarationCatalogEntry> classDeclarations() {
        return List.of();
    }

    /** Returns exact TypeScript Bean alias exclusions collected during bootstrap. */
    default List<BeanAliasExclusionCatalogEntry> beanAliasExclusions() {
        return List.of();
    }

    /** Returns exact TypeScript method-to-Bean projections collected during bootstrap. */
    default List<BeanMethodProjectionCatalogEntry> beanMethodProjections() {
        return List.of();
    }

    /**
     * Structured entries for the typed builder API. They are derived by reflection from the
     * version-tree contracts and rendered into the TypeScript and Python declarations from the same
     * source. The empty default keeps existing implementors, test stubs included, compiling;
     * {@code NekoPluginRuntime} merges and supplies the real entries.
     */
    default List<RegistryBuilderSurfaceEntry> registryBuilderSurfaces() {
        return List.of();
    }

    /** JS modules registered by the plugin: moduleId to CommonJS source. */
    Map<String, String> nodeModules();

    Map<String, RecipeNamespaceEntry> recipeNamespaces();

    void beforeRecipeLoading(RecipeLifecycleContext context);

    void afterRecipes(RecipeLifecycleContext context);

    void fireInit();

    void fireInitStartup();

    void fireAfterInit();

    void fireBeforeScriptsLoaded(ScriptType type);

    void fireAfterScriptsLoaded(ScriptType type);

    /** Returns the managed API runtime view for the given environment, or null if no managed API was bootstrapped. */
    ApiRuntimeView apiRuntime(EnvironmentKey environment);

    /** Returns the implementation backing a managed global, or null if the global has no implementation. */
    Object managedApiImplementation(ApiSymbolId globalId);
}
