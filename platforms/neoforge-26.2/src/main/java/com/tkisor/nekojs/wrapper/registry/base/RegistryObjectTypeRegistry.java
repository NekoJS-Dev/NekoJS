package com.tkisor.nekojs.wrapper.registry.base;

import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.jetbrains.annotations.ApiStatus;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
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

    /**
     * @author ZZZank
     */
    @ApiStatus.Internal
    class Impl implements RegistryObjectTypeRegistry {
        public final RegistryInfos registryInfos;
        private final Map<ResourceKey<Registry<?>>, Map<String, RegistryObjectType<?>>> types = new LinkedHashMap<>();

        public Impl(RegistryInfos registryInfos) {
            this.registryInfos = Objects.requireNonNull(registryInfos, "registryInfos");
        }

        @Override
        public <T> Scope<T> scope(ResourceKey<Registry<T>> key) {
            Objects.requireNonNull(key, "key");
            return new ScopeImpl<>(key);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> void register(RegistryObjectType<T> type) {
            Objects.requireNonNull(type, "type");
            var key = (ResourceKey<Registry<?>>) (ResourceKey<?>) type.resourceKey();
            types.computeIfAbsent(key, k -> new HashMap<>()).put(type.type(), type);
        }

        @Override
        public Map<ResourceKey<Registry<?>>, Map<String, RegistryObjectType<?>>> view() {
            return types;
        }

        private class ScopeImpl<T> implements Scope<T> {
            private final ResourceKey<Registry<T>> key;
            private final RegistryInfo<T> registryInfo;
            private final Map<String, RegistryObjectType<?>> scopeTypes;

            ScopeImpl(ResourceKey<Registry<T>> key) {
                this.key = key;
                this.registryInfo = registryInfos.get(key);

                var existing = types.get(key);
                this.scopeTypes = existing != null ? existing : new HashMap<>();

                @SuppressWarnings("unchecked")
                var castedKey = (ResourceKey<Registry<?>>) (ResourceKey<?>) key;
                types.put(castedKey, scopeTypes);
            }

            @Override
            public ResourceKey<Registry<T>> key() {
                return key;
            }

            @Override
            public void register(String type, BiFunction<RegistryInfo<T>, Identifier, RegistryObjectBuilder<T>> factory) {
                Objects.requireNonNull(type, "type");
                Objects.requireNonNull(factory, "factory");
                var objectType = new RegistryObjectType.Impl<>(key, registryInfo, type, factory);
                scopeTypes.put(type, objectType);
            }

            @Override
            public void close() {
                // AutoCloseable - no cleanup needed
            }
        }
    }
}
