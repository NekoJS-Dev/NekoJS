package com.tkisor.nekojs.api.catalog;

import java.util.List;

/**
 * Describes a game registry for @special type generation.
 * Generates literal union types like:
 * <pre>
 * type Block = "minecraft:stone" | "minecraft:dirt" | ...;
 * </pre>
 *
 * @param typeName   the TypeScript type name (e.g. "Block", "Item")
 * @param entries    all registry entry ids (e.g. "minecraft:stone")
 * @param tagEntries all tag ids for this registry (e.g. "minecraft:planks")
 */
public record RegistryTypeCatalogEntry(
        String typeName,
        List<String> entries,
        List<String> tagEntries
) {
    public RegistryTypeCatalogEntry {
        entries = List.copyOf(entries);
        tagEntries = List.copyOf(tagEntries == null ? List.of() : tagEntries);
    }
}
