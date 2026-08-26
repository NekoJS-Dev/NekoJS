package com.tkisor.nekojs.wrapper.registry.base;

import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

import java.util.Map;
import java.util.function.BiFunction;

/**
 * @author ZZZank
 */
public interface RegistryObjectTypeRegistry {
    <T> Scope<T> scope(ResourceKey<Registry<T>> key);

    <T> void register(RegistryObjectType<T> type);

    /// [RegistryEventJS] will use values here, aka `Map<String, RegistryObjectType<?>>`
    Map<ResourceKey<Registry<?>>, Map<String, RegistryObjectType<?>>> view();

    interface Scope<T> extends AutoCloseable {
        ResourceKey<Registry<T>> key();

        void register(String type, BiFunction<RegistryInfo<T>, Identifier, RegistryObjectBuilder<T>> factory);

        @Override
        void close();
    }
}
