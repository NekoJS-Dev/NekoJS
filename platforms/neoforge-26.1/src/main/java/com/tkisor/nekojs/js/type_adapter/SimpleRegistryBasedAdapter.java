package com.tkisor.nekojs.js.type_adapter;

import com.tkisor.nekojs.api.JSTypeAdapter;
import com.tkisor.nekojs.api.data.NekoId;
import graal.graalvm.polyglot.Value;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
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
    public boolean canConvert(Value value) {
        if (value.isString()) {
            return true;
        }
        if (value.isHostObject()) {
            var hostObject = value.asHostObject();
            return hostObject instanceof NekoId || hostObject instanceof Identifier;
        }
        return false;
    }

    @Override
    public T convert(Value value) {
        if (value.isString()) {
            return getFromRegistry(value, Identifier.parse(value.asString()));
        }
        if (value.isHostObject()) {
            var hostObject = value.asHostObject();
            if (hostObject instanceof NekoId(String namespace, String path)) {
                return getFromRegistry(value, Identifier.fromNamespaceAndPath(namespace, path));
            } else if (hostObject instanceof Identifier identifier) {
                return getFromRegistry(value, identifier);
            }
        }
        return null;
    }

    private @NonNull T getFromRegistry(Value value, Identifier id) {
        return registry.getOptional(id)
            .orElseThrow(() -> new IllegalArgumentException(String.format(
                "No object with id '%s' in registry '%s'",
                value.asString(),
                registry.key().identifier()
            )));
    }
}
