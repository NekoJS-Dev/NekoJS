package com.tkisor.nekojs.api.catalog;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.surface.ApiEnvironmentSnapshot;
import com.tkisor.nekojs.api.surface.ApiSymbol;

import java.util.List;
import java.util.Map;

public record NekoScriptCatalogSnapshot(
        List<ScriptType> scriptTypes,
        List<BindingCatalogEntry> bindings,
        List<EventCatalogEntry> events,
        List<AdapterCatalogEntry> adapters,
        List<RecipeNamespaceCatalogEntry> recipeNamespaces,
        List<HostExtensionCatalogEntry> hostExtensions,
        List<SnippetCatalogEntry> snippets,
        List<TypeDocCatalogEntry> typeDocs,
        List<ManualDeclarationCatalogEntry> manualDeclarations,
        List<RegistryTypeCatalogEntry> registryTypes,
        List<String> modIds,
        TypeOutputLayout outputLayout,
        Map<ScriptType, ApiEnvironmentSnapshot> managedApis,
        List<ApiSymbol> legacySurface
) {
    public NekoScriptCatalogSnapshot {
        modIds = List.copyOf(modIds == null ? List.of() : modIds);
        managedApis = Map.copyOf(managedApis == null ? Map.of() : managedApis);
        legacySurface = List.copyOf(legacySurface == null ? List.of() : legacySurface);
    }
}
