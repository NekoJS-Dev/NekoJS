package com.tkisor.nekojs.api.registry;

import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

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
        return registryAccess.registry(resourceKey);
    }

    public Registry<T> getRegistryOrThrow(RegistryAccess registryAccess) {
        return registryAccess.registryOrThrow(resourceKey);
    }

    public ResourceLocation identifier() {
        return resourceKey.location();
    }
}
