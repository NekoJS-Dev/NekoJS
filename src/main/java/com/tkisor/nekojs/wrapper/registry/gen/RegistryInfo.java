package com.tkisor.nekojs.wrapper.registry.gen;
//~ mc_legacy_api

import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/**
 * Metadata for one registry, produced by reflecting a contributed scan root.
 *
 * @param key   the registry resource key, such as {@code minecraft:item}
 * @param token the base type token the registry holds, such as {@code Item.class}, used for
 *              diagnostics and for type factory checks
 */
public record RegistryInfo(ResourceKey<? extends Registry<?>> key, Class<?> token) {

    /**
     * Method name this registry gets on the script sugar object: the snake_case path segment of the
     * ResourceKey becomes lowerCamelCase ({@code entity_type} → {@code entityType}), matching the
     * naming of the old typed entry point. When several paths map to the same sugar method, the
     * first one wins (LinkedHashMap put-if-absent semantics).
     */
    public String sugarName() {
        String[] parts = key.identifier().getPath().split("_");
        StringBuilder name = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            if (parts[i].isEmpty()) {
                continue;
            }
            name.append(Character.toUpperCase(parts[i].charAt(0))).append(parts[i].substring(1));
        }
        return name.toString();
    }

    /** Convenience factory. */
    public static RegistryInfo of(ResourceKey<? extends Registry<?>> key, Class<?> token) {
        return new RegistryInfo(key, token);
    }

    /** The full key that ties an object id to this registry, for diagnostics. */
    public Identifier objectId(Identifier id) {
        return id;
    }
}
