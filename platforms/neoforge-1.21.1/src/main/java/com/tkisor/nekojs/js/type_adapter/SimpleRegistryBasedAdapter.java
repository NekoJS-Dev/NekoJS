package com.tkisor.nekojs.js.type_adapter;

import com.tkisor.nekojs.api.JSTypeAdapter;
import com.tkisor.nekojs.api.data.NekoId;
import graal.graalvm.polyglot.Value;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import org.jspecify.annotations.NonNull;

/**
 * @author ZZZank
 */
public record SimpleRegistryBasedAdapter<T>(Registry<T> registry, Class<T> targetType) implements JSTypeAdapter<T> {
    @Override
    public Class<T> getTargetClass() {
        return targetType;
    }

    @Override
    public boolean test(Value value) {
        if (value.isString()) {
            return true;
        }
        if (value.isHostObject()) {
            var hostObject = value.asHostObject();
            return hostObject instanceof NekoId || hostObject instanceof ResourceLocation;
        }
        return false;
    }

    @Override
    public T apply(Value value) {
        if (value.isString()) {
            return getFromRegistry(value, ResourceLocation.parse(value.asString()));
        }
        if (value.isHostObject()) {
            var hostObject = value.asHostObject();
            if (hostObject instanceof NekoId(String namespace, String path)) {
                return getFromRegistry(value, ResourceLocation.fromNamespaceAndPath(namespace, path));
            } else if (hostObject instanceof ResourceLocation location) {
                return getFromRegistry(value, location);
            }
        }
        return null;
    }

    private @NonNull T getFromRegistry(Value value, ResourceLocation id) {
        return registry.getOptional(id)
            .orElseThrow(() -> new IllegalArgumentException(String.format(
                "No object with id '%s' in registry '%s'",
                value.isString() ? value.asString() : id,
                registry.key().location()
            )));
    }
}
