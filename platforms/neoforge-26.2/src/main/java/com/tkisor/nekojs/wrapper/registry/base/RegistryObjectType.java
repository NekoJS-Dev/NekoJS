package com.tkisor.nekojs.wrapper.registry.base;

import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;
import java.util.function.BiFunction;

/**
 * @author ZZZank
 */
public interface RegistryObjectType<T> {

    ResourceKey<Registry<T>> resourceKey();

    RegistryInfo<T> registryInfo();

    String type();

    RegistryObjectBuilder<T> createBuilder(Identifier id);

    /**
     * @author ZZZank
     */
    @ApiStatus.Internal
    record Impl<T>(
        ResourceKey<Registry<T>> resourceKey,
        RegistryInfo<T> registryInfo,
        String type,
        BiFunction<RegistryInfo<T>, Identifier, RegistryObjectBuilder<T>> factory
    ) implements RegistryObjectType<T> {

        @Override
        public RegistryObjectBuilder<T> createBuilder(Identifier id) {
            Objects.requireNonNull(id, "id");
            return factory.apply(registryInfo, id);
        }
    }
}
