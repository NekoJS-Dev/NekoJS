package com.tkisor.nekojs.api.catalog;

import java.util.Collection;
import java.util.List;

public interface NekoCatalogPlatformProvider {
    NekoCatalogPlatformProvider EMPTY = new NekoCatalogPlatformProvider() {};

    default Collection<RecipeNamespaceCatalogEntry> recipeNamespaces() {
        return List.of();
    }

    default Collection<HostExtensionSource> hostExtensions() {
        return List.of();
    }

    default Collection<SnippetCatalogEntry> snippets() {
        return List.of();
    }

    default TypeOutputLayout outputLayout() {
        return null;
    }
}
