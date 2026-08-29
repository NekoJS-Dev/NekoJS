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

    /**
     * Loaded mod ids, one of the two sources of the {@code RegistryTypes.Namespace} literal
     * union ({@code "@create"}-style namespace filters).
     *
     * <p>Comes from the loader's mod list, which covers mods that register nothing into a
     * given registry. The probe merges it with the namespaces of the collected registry entry
     * ids, which cover namespaces owned by no mod (scripts, datapacks).
     */
    default Collection<String> modIds() {
        return List.of();
    }

    default TypeOutputLayout outputLayout() {
        return null;
    }
}
