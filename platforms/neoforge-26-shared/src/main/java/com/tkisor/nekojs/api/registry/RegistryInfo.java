package com.tkisor.nekojs.api.registry;

import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

import java.lang.reflect.Type;
import java.util.Optional;

/**
 * @author ZZZank
 */
public record RegistryInfo<T>(
    Class<T> objectBaseType,
    Type rawType,
    ResourceKey<Registry<T>> resourceKey
) {
    public Optional<Registry<T>> getRegistry(RegistryAccess registryAccess) {
        return registryAccess.lookup(resourceKey);
    }

    public Registry<T> getRegistryOrThrow(RegistryAccess registryAccess) {
        var key = resourceKey;
        return registryAccess.lookup(key).orElseThrow(() -> new IllegalStateException("No registry found for: " + key.identifier()));
    }

    public Identifier identifier() {
        return resourceKey.identifier();
    }
}
