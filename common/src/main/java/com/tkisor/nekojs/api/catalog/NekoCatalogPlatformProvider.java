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

    /**
     * Game registries for @special literal union type generation.
     * Returns registry entries (items, blocks, fluids, etc.) that become
     * types like {@code type Block = "minecraft:stone" | ...}.
     */
    default Collection<RegistryTypeCatalogEntry> registryTypes() {
        return List.of();
    }

    default TypeOutputLayout outputLayout() {
        return null;
    }
}
