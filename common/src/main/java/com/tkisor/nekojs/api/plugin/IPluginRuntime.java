package com.tkisor.nekojs.api.plugin;

import com.tkisor.nekojs.api.JSTypeAdapter;
import com.tkisor.nekojs.api.catalog.ManualDeclarationCatalogEntry;
import com.tkisor.nekojs.api.catalog.ClassDeclarationCatalogEntry;
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
     * 插件注册的**类声明替换**：把指定类的 probe 生成结果整体换成手写声明。
     *
     * <p>与 {@link #manualDeclarations()} 的区别：后者往 {@code @manual/index.d.ts} 追加，
     * 碰不到 {@code @package/.../index.d.ts} 里已生成的类声明；本方法在生成端替换。
     * 默认空表——不注册替换的实现（含测试替身）无需覆盖。
     */
    default List<ClassDeclarationCatalogEntry> classDeclarations() {
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
