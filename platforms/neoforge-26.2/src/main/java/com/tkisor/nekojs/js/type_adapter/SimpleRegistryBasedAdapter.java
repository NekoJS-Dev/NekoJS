package com.tkisor.nekojs.js.type_adapter;

import com.tkisor.nekojs.api.data.AbstractJSTypeAdapter;
import com.tkisor.nekojs.api.data.NekoId;
import com.tkisor.nekojs.api.data.ValueConversionException;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

/**
 * 基于 {@link Registry} 的通用适配器。从 {@code record} 迁移为 {@link AbstractJSTypeAdapter} 子类，
 * 构造签名 {@code new SimpleRegistryBasedAdapter<>(registry, type)} 保持不变（调用方零改动）。
 *
 * @author ZZZank
 */
public class SimpleRegistryBasedAdapter<T> extends AbstractJSTypeAdapter<T> {
    private final Registry<T> registry;
    private final Class<T> targetType;

    public SimpleRegistryBasedAdapter(Registry<T> registry, Class<T> targetType) {
        this.registry = registry;
        this.targetType = targetType;
    }

    @Override
    public Class<T> getTargetClass() {
        return targetType;
    }

    @Override
    protected T fromString(String s) {
        Identifier id = Identifier.parse(s);
        return getFromRegistry(id, s);
    }

    @Override
    protected T fromHostObject(Object host) {
        if (host instanceof NekoId(String namespace, String path)) {
            Identifier id = Identifier.fromNamespaceAndPath(namespace, path);
            return getFromRegistry(id, id.toString());
        }
        if (host instanceof Identifier identifier) {
            return getFromRegistry(identifier, identifier.toString());
        }
        if (targetType.isInstance(host)) {
            return targetType.cast(host);
        }
        return null; // 不识别
    }

    private @NonNull T getFromRegistry(Identifier id, String display) {
        return registry.getOptional(id)
            .orElseThrow(() -> new ValueConversionException(targetType, "registered " + targetType.getSimpleName() + " id",
                display, "no object with id '" + display + "' in registry '" + registry.key().identifier() + "'"));
    }
}
