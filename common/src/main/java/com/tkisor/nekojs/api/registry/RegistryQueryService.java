package com.tkisor.nekojs.api.registry;

import java.util.List;

/**
 * Read-only query SPI over the Minecraft registries.
 *
 * <p>Each platform implements it and returns plain values (id strings, booleans); it exposes no
 * Minecraft types. Used by the {@code Registry} script binding.
 */
public interface RegistryQueryService {
    /** Whether the given registry exists (for example {@code minecraft:item}). */
    boolean hasRegistry(String registryId);

    /** All entry ids in the registry, namespaced, in the registry's own order. */
    List<String> all(String registryId);

    /** Whether the registry contains the given id (for example {@code minecraft:stone}). */
    boolean has(String registryId, String id);

    /** All entry ids under the given tag (for example the tag {@code minecraft:planks}). */
    List<String> tag(String registryId, String tagId);

    /**
     * All data map type ids registered for the given registry (for example
     * {@code neoforge:furnace_fuels}). Returns an empty list when the registry is unknown or the
     * platform has no data maps, such as 1.12.2.
     */
    default List<String> dataMapIds(String registryId) {
        return List.of();
    }

    /**
     * Reads a data map value for the given registry entry and serializes it to a JSON string.
     * Returns {@code null} when the entry, the data map type or the registry does not exist.
     */
    default String dataMapValue(String registryId, String dataMapTypeId, String id) {
        return null;
    }
}
