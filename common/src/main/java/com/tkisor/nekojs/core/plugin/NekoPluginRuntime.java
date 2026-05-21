package com.tkisor.nekojs.core.plugin;

import com.tkisor.nekojs.api.JSTypeAdapter;
import com.tkisor.nekojs.api.NekoJSBasePlugin;
import com.tkisor.nekojs.api.catalog.ManualDeclarationCatalogEntry;
import com.tkisor.nekojs.api.catalog.TypeDocCatalogEntry;
import com.tkisor.nekojs.api.compiler.ScriptCompilerRegistry;
import com.tkisor.nekojs.api.data.Binding;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.api.recipe.RecipeLifecycleContext;
import com.tkisor.nekojs.api.recipe.RecipeNamespaceEntry;
import com.tkisor.nekojs.script.ScriptType;
import com.tkisor.nekojs.script.prop.ScriptPropertyRegistry;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class NekoPluginRuntime {
    private static NekoPluginRuntime current;

    private final ScriptCompilerRegistry scriptCompilers;
    private final ScriptPropertyRegistry scriptProperties;
    private final Map<ScriptType, Map<String, Binding>> bindings;
    private final List<JSTypeAdapter<?>> adapters;
    private final Map<String, EventGroup> eventGroups;
    private final List<TypeDocCatalogEntry> typeDocs;
    private final List<ManualDeclarationCatalogEntry> manualDeclarations;
    private final Map<String, RecipeNamespaceEntry<?>> recipeNamespaces;
    private final List<Consumer<RecipeLifecycleContext>> beforeRecipeLoadingHooks;
    private final List<Consumer<RecipeLifecycleContext>> afterRecipesHooks;

    private NekoPluginRuntime(ScriptCompilerRegistry scriptCompilers,
                              ScriptPropertyRegistry scriptProperties,
                              Map<ScriptType, Map<String, Binding>> bindings,
                              List<JSTypeAdapter<?>> adapters,
                              Map<String, EventGroup> eventGroups,
                              List<TypeDocCatalogEntry> typeDocs,
                              List<ManualDeclarationCatalogEntry> manualDeclarations,
                              Map<String, RecipeNamespaceEntry<?>> recipeNamespaces,
                              List<Consumer<RecipeLifecycleContext>> beforeRecipeLoadingHooks,
                              List<Consumer<RecipeLifecycleContext>> afterRecipesHooks) {
        this.scriptCompilers = scriptCompilers;
        this.scriptProperties = scriptProperties;
        this.bindings = bindings;
        this.adapters = adapters;
        this.eventGroups = eventGroups;
        this.typeDocs = typeDocs;
        this.manualDeclarations = manualDeclarations;
        this.recipeNamespaces = recipeNamespaces;
        this.beforeRecipeLoadingHooks = beforeRecipeLoadingHooks;
        this.afterRecipesHooks = afterRecipesHooks;
    }

    public static NekoPluginRuntime bootstrap(List<NekoJSBasePlugin> plugins) {
        NekoPluginRuntime runtime = NekoPluginBootstrap.bootstrap(plugins);
        current = runtime;
        ScriptCompilerRegistry.useRuntime(runtime.scriptCompilers());
        return runtime;
    }

    public static NekoPluginRuntime current() {
        if (current == null) {
            throw new IllegalStateException("NekoPluginRuntime has not been bootstrapped yet");
        }
        return current;
    }

    static NekoPluginRuntime create(ScriptCompilerRegistry scriptCompilers,
                                    ScriptPropertyRegistry scriptProperties,
                                    Map<ScriptType, Map<String, Binding>> bindings,
                                    List<JSTypeAdapter<?>> adapters,
                                    Map<String, EventGroup> eventGroups,
                                    List<TypeDocCatalogEntry> typeDocs,
                                    List<ManualDeclarationCatalogEntry> manualDeclarations,
                                    Map<String, RecipeNamespaceEntry<?>> recipeNamespaces,
                                    List<Consumer<RecipeLifecycleContext>> beforeRecipeLoadingHooks,
                                    List<Consumer<RecipeLifecycleContext>> afterRecipesHooks) {
        return new NekoPluginRuntime(scriptCompilers, scriptProperties, bindings, adapters, eventGroups, typeDocs, manualDeclarations, recipeNamespaces, beforeRecipeLoadingHooks, afterRecipesHooks);
    }

    public ScriptCompilerRegistry scriptCompilers() {
        return scriptCompilers;
    }

    public ScriptPropertyRegistry scriptProperties() {
        return scriptProperties;
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

    public Map<String, RecipeNamespaceEntry<?>> recipeNamespaces() {
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
            hook.accept(context);
        }
    }

}
