package com.tkisor.nekojs.api.catalog;

import com.tkisor.nekojs.script.ScriptType;

import java.util.List;

public record NekoScriptCatalogSnapshot(
        List<ScriptType> scriptTypes,
        List<BindingCatalogEntry> bindings,
        List<EventCatalogEntry> events,
        List<AdapterCatalogEntry> adapters,
        List<RecipeNamespaceCatalogEntry> recipeNamespaces,
        List<HostExtensionCatalogEntry> hostExtensions,
        List<SnippetCatalogEntry> snippets,
        TypeOutputLayout outputLayout
) {
}
