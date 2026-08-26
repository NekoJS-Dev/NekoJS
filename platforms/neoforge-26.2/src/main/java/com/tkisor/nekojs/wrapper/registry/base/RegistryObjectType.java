package com.tkisor.nekojs.wrapper.registry.base;

import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/**
 * @author ZZZank
 */
public interface RegistryObjectType<T> {

    ResourceKey<Registry<T>> resourceKey();

    String type();

    RegistryObjectBuilder<T> createBuilder(RegistryInfo<T> info, Identifier id);
}
