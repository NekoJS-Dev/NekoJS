package com.tkisor.nekojs.api.plugin;

import com.tkisor.nekojs.api.JSTypeAdapter;
import com.tkisor.nekojs.api.catalog.ManualDeclarationCatalogEntry;
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

    Map<String, EventGroup> eventGroups();

    List<JSTypeAdapter<?>> adapters();

    List<TypeDocCatalogEntry> typeDocs();

    List<ManualDeclarationCatalogEntry> manualDeclarations();

    /**
     * typed Builder 面的结构化契约条目（ticket 15）：版本树契约反射派生，TS/Python declaration 同源渲染。
     * default 空实现保持既有实现方（含测试 stub）兼容——真实产物由 {@code NekoPluginRuntime} 合并提供。
     */
    default List<RegistryBuilderSurfaceEntry> registryBuilderSurfaces() {
        return List.of();
    }

    /** 插件注册的 JS 模块：moduleId → CommonJS source。 */
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
