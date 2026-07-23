package com.tkisor.nekojs.api.registry;

import net.minecraft.util.ResourceLocation;
import net.minecraftforge.registries.IForgeRegistry;

import java.util.Objects;

/**
 * 1.12.2 RegistryInfo - wraps an IForgeRegistry for type-safe registry access.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public final class RegistryInfo<T> {
    private final Class<T> objectBaseType;
    private final IForgeRegistry registry;
    private final ResourceLocation registryName;

    public RegistryInfo(Class<T> objectBaseType, IForgeRegistry registry, ResourceLocation registryName) {
        this.objectBaseType = Objects.requireNonNull(objectBaseType);
        this.registry = Objects.requireNonNull(registry);
        this.registryName = registryName;
    }

    public Class<T> getObjectBaseType() {
        return objectBaseType;
    }

    public IForgeRegistry getRegistry() {
        return registry;
    }

    public ResourceLocation getRegistryName() {
        return registryName;
    }

    public T getValue(ResourceLocation id) {
        return (T) registry.getValue(id);
    }

    public boolean containsKey(ResourceLocation id) {
        return registry.containsKey(id);
    }
}
