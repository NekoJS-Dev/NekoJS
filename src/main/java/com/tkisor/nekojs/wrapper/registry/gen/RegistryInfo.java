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

    /** Convenience factory. */
    public static RegistryInfo of(ResourceKey<? extends Registry<?>> key, Class<?> token) {
        return new RegistryInfo(key, token);
    }

    /** The full key that ties an object id to this registry, for diagnostics. */
    public Identifier objectId(Identifier id) {
        return id;
    }
}
