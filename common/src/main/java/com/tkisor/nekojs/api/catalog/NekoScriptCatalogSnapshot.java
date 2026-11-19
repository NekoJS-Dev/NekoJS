package com.tkisor.nekojs.api.catalog;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.surface.ApiEnvironmentSnapshot;
import com.tkisor.nekojs.api.surface.ApiSymbol;

import java.util.List;
import java.util.Map;

/**
 * Immutable snapshot of all bindings, events, adapters, recipes, and type metadata exposed to scripts and probe generators.
 */
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
        List<ClassDeclarationCatalogEntry> classDeclarations,
        List<RegistryBuilderSurfaceEntry> registryBuilderSurfaces,
        List<RegistryTypeCatalogEntry> registryTypes,
        List<String> modIds,
        TypeOutputLayout outputLayout,
        Map<ScriptType, ApiEnvironmentSnapshot> managedApis,
        List<ApiSymbol> legacySurface,
        List<BeanAliasExclusionCatalogEntry> beanAliasExclusions,
        List<BeanMethodProjectionCatalogEntry> beanMethodProjections,
        List<ModuleBindingCatalogEntry> moduleBindings,
        List<ModuleEventCatalogEntry> moduleEvents
) {
    public NekoScriptCatalogSnapshot {
        scriptTypes = List.copyOf(scriptTypes == null ? List.of() : scriptTypes);
        bindings = List.copyOf(bindings == null ? List.of() : bindings);
        events = List.copyOf(events == null ? List.of() : events);
        adapters = List.copyOf(adapters == null ? List.of() : adapters);
        recipeNamespaces = List.copyOf(recipeNamespaces == null ? List.of() : recipeNamespaces);
        hostExtensions = List.copyOf(hostExtensions == null ? List.of() : hostExtensions);
        snippets = List.copyOf(snippets == null ? List.of() : snippets);
        typeDocs = List.copyOf(typeDocs == null ? List.of() : typeDocs);
        manualDeclarations = List.copyOf(manualDeclarations == null ? List.of() : manualDeclarations);
        registryBuilderSurfaces = List.copyOf(registryBuilderSurfaces == null ? List.of() : registryBuilderSurfaces);
        registryTypes = List.copyOf(registryTypes == null ? List.of() : registryTypes);
        moduleEvents = List.copyOf(moduleEvents == null ? List.of() : moduleEvents);
        moduleBindings = List.copyOf(moduleBindings == null ? List.of() : moduleBindings);
        beanMethodProjections = List.copyOf(beanMethodProjections == null ? List.of() : beanMethodProjections);
        beanAliasExclusions = List.copyOf(beanAliasExclusions == null ? List.of() : beanAliasExclusions);
        classDeclarations = ClassDeclarationCatalogEntry.resolve(classDeclarations == null ? List.of() : classDeclarations);
        modIds = List.copyOf(modIds == null ? List.of() : modIds);
        managedApis = Map.copyOf(managedApis == null ? Map.of() : managedApis);
        legacySurface = List.copyOf(legacySurface == null ? List.of() : legacySurface);
    }

    /** Preserves the constructor shape before module event publications were added. */
    public NekoScriptCatalogSnapshot(
            List<ScriptType> scriptTypes, List<BindingCatalogEntry> bindings,
            List<EventCatalogEntry> events, List<AdapterCatalogEntry> adapters,
            List<RecipeNamespaceCatalogEntry> recipeNamespaces, List<HostExtensionCatalogEntry> hostExtensions,
            List<SnippetCatalogEntry> snippets, List<TypeDocCatalogEntry> typeDocs,
            List<ManualDeclarationCatalogEntry> manualDeclarations,
            List<ClassDeclarationCatalogEntry> classDeclarations,
            List<RegistryBuilderSurfaceEntry> registryBuilderSurfaces,
            List<RegistryTypeCatalogEntry> registryTypes, List<String> modIds,
            TypeOutputLayout outputLayout, Map<ScriptType, ApiEnvironmentSnapshot> managedApis,
            List<ApiSymbol> legacySurface, List<BeanAliasExclusionCatalogEntry> beanAliasExclusions,
            List<BeanMethodProjectionCatalogEntry> beanMethodProjections,
            List<ModuleBindingCatalogEntry> moduleBindings) {
        this(scriptTypes, bindings, events, adapters, recipeNamespaces, hostExtensions, snippets,
                typeDocs, manualDeclarations, classDeclarations, registryBuilderSurfaces, registryTypes,
                modIds, outputLayout, managedApis, legacySurface, beanAliasExclusions, beanMethodProjections, moduleBindings, List.of());
    }

    /** Preserves the constructor shape before module-only bindings were added. */
    public NekoScriptCatalogSnapshot(
            List<ScriptType> scriptTypes, List<BindingCatalogEntry> bindings,
            List<EventCatalogEntry> events, List<AdapterCatalogEntry> adapters,
            List<RecipeNamespaceCatalogEntry> recipeNamespaces, List<HostExtensionCatalogEntry> hostExtensions,
            List<SnippetCatalogEntry> snippets, List<TypeDocCatalogEntry> typeDocs,
            List<ManualDeclarationCatalogEntry> manualDeclarations,
            List<ClassDeclarationCatalogEntry> classDeclarations,
            List<RegistryBuilderSurfaceEntry> registryBuilderSurfaces,
            List<RegistryTypeCatalogEntry> registryTypes, List<String> modIds,
            TypeOutputLayout outputLayout, Map<ScriptType, ApiEnvironmentSnapshot> managedApis,
            List<ApiSymbol> legacySurface, List<BeanAliasExclusionCatalogEntry> beanAliasExclusions,
            List<BeanMethodProjectionCatalogEntry> beanMethodProjections) {
        this(scriptTypes, bindings, events, adapters, recipeNamespaces, hostExtensions, snippets,
                typeDocs, manualDeclarations, classDeclarations, registryBuilderSurfaces, registryTypes,
                modIds, outputLayout, managedApis, legacySurface, beanAliasExclusions, beanMethodProjections, List.of());
    }

    /** Preserves the constructor shape before exact method-to-Bean projections were added. */
    public NekoScriptCatalogSnapshot(
            List<ScriptType> scriptTypes, List<BindingCatalogEntry> bindings,
            List<EventCatalogEntry> events, List<AdapterCatalogEntry> adapters,
            List<RecipeNamespaceCatalogEntry> recipeNamespaces, List<HostExtensionCatalogEntry> hostExtensions,
            List<SnippetCatalogEntry> snippets, List<TypeDocCatalogEntry> typeDocs,
            List<ManualDeclarationCatalogEntry> manualDeclarations,
            List<ClassDeclarationCatalogEntry> classDeclarations,
            List<RegistryBuilderSurfaceEntry> registryBuilderSurfaces,
            List<RegistryTypeCatalogEntry> registryTypes, List<String> modIds,
            TypeOutputLayout outputLayout, Map<ScriptType, ApiEnvironmentSnapshot> managedApis,
            List<ApiSymbol> legacySurface, List<BeanAliasExclusionCatalogEntry> beanAliasExclusions) {
        this(scriptTypes, bindings, events, adapters, recipeNamespaces, hostExtensions, snippets,
                typeDocs, manualDeclarations, classDeclarations, registryBuilderSurfaces, registryTypes,
                modIds, outputLayout, managedApis, legacySurface, beanAliasExclusions, List.of());
    }

    /** Preserves the constructor shape before exact Bean alias exclusions were added. */
    public NekoScriptCatalogSnapshot(
            List<ScriptType> scriptTypes, List<BindingCatalogEntry> bindings,
            List<EventCatalogEntry> events, List<AdapterCatalogEntry> adapters,
            List<RecipeNamespaceCatalogEntry> recipeNamespaces, List<HostExtensionCatalogEntry> hostExtensions,
            List<SnippetCatalogEntry> snippets, List<TypeDocCatalogEntry> typeDocs,
            List<ManualDeclarationCatalogEntry> manualDeclarations,
            List<ClassDeclarationCatalogEntry> classDeclarations,
            List<RegistryBuilderSurfaceEntry> registryBuilderSurfaces,
            List<RegistryTypeCatalogEntry> registryTypes, List<String> modIds,
            TypeOutputLayout outputLayout, Map<ScriptType, ApiEnvironmentSnapshot> managedApis,
            List<ApiSymbol> legacySurface) {
        this(scriptTypes, bindings, events, adapters, recipeNamespaces, hostExtensions, snippets,
                typeDocs, manualDeclarations, classDeclarations, registryBuilderSurfaces, registryTypes,
                modIds, outputLayout, managedApis, legacySurface, List.of());
    }

    /** Preserves the constructor shape before authored class declarations were added. */
    public NekoScriptCatalogSnapshot(
            List<ScriptType> scriptTypes, List<BindingCatalogEntry> bindings,
            List<EventCatalogEntry> events, List<AdapterCatalogEntry> adapters,
            List<RecipeNamespaceCatalogEntry> recipeNamespaces, List<HostExtensionCatalogEntry> hostExtensions,
            List<SnippetCatalogEntry> snippets, List<TypeDocCatalogEntry> typeDocs,
            List<ManualDeclarationCatalogEntry> manualDeclarations,
            List<RegistryBuilderSurfaceEntry> registryBuilderSurfaces,
            List<RegistryTypeCatalogEntry> registryTypes, List<String> modIds,
            TypeOutputLayout outputLayout, Map<ScriptType, ApiEnvironmentSnapshot> managedApis,
            List<ApiSymbol> legacySurface) {
        this(scriptTypes, bindings, events, adapters, recipeNamespaces, hostExtensions, snippets,
                typeDocs, manualDeclarations, List.of(), registryBuilderSurfaces, registryTypes,
                modIds, outputLayout, managedApis, legacySurface);
    }

}
