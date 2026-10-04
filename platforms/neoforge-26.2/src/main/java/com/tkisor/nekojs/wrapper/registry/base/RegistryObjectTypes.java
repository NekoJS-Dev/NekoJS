package com.tkisor.nekojs.wrapper.registry.base;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import org.jetbrains.annotations.ApiStatus;

import java.util.Map;

/**
 * @author ZZZank
 */
public interface RegistryObjectTypes<T> {
    @SuppressWarnings("unchecked")
    static <T> RegistryObjectTypes<T> get(ResourceKey<Registry<T>> key) {
        return (RegistryObjectTypes<T>) Internal.REGISTERED.get(key);
    }

    RegistryInfo<T> registryInfo();

    RegistryObjectType<T> get(String type);

    Map<String, RegistryObjectType<T>> view();

    @ApiStatus.Internal
    record Impl<T>(
        RegistryInfo<T> registryInfo,
        Map<String, RegistryObjectType<T>> view
    ) implements RegistryObjectTypes<T> {

        @Override
        public RegistryObjectType<T> get(String type) {
            return view.get(type);
        }
    }

    @ApiStatus.Internal
    abstract class Internal {
        public static Map<ResourceKey<Registry<?>>, RegistryObjectTypes<?>> REGISTERED;
    }
}
