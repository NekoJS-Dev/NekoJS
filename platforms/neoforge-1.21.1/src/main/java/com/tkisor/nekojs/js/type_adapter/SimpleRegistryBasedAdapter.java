package com.tkisor.nekojs.js.type_adapter;

import com.tkisor.nekojs.api.data.AbstractJSTypeAdapter;
import com.tkisor.nekojs.api.data.ValueConversionException;
import com.tkisor.nekojs.api.data.NekoId;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;

/**
 * 基于注册表的通用适配器。修复 B1：从 {@code record}（无法继承基类、apply 走旧样板）改为
 * {@code class extends AbstractJSTypeAdapter<T>}，复用 test/apply final 模板。
 *
 * <p>构造形态保留 {@code new SimpleRegistryBasedAdapter<>(registry, type)}，向后兼容。
 *
 * @author ZZZank
 */
public final class SimpleRegistryBasedAdapter<T> extends AbstractJSTypeAdapter<T> {

    private final Registry<T> registry;
    private final Class<T> targetType;

    public SimpleRegistryBasedAdapter(Registry<T> registry, Class<T> targetType) {
        this.registry = registry;
        this.targetType = targetType;
    }

    public Registry<T> registry() {
        return registry;
    }

    public Class<T> targetType() {
        return targetType;
    }

    @Override
    public Class<T> getTargetClass() {
        return targetType;
    }

    @Override
    protected T fromString(String s) {
        // 1.21.1: ResourceLocation.parse 非法时直接抛异常；先 tryParse 做统一错误信息。
        ResourceLocation id = ResourceLocation.tryParse(s);
        if (id == null) {
            throw new ValueConversionException(targetType, "registry id string", s,
                "invalid resource location: " + s);
        }
        return getFromRegistry(id);
    }

    @Override
    protected T fromHostObject(Object host) {
        if (host instanceof NekoId(String namespace, String path)) {
            return getFromRegistry(ResourceLocation.fromNamespaceAndPath(namespace, path));
        }
        if (host instanceof ResourceLocation location) {
            return getFromRegistry(location);
        }
        if (targetType.isInstance(host)) {
            return targetType.cast(host);
        }
        return null; // 不识别
    }

    private T getFromRegistry(ResourceLocation id) {
        return registry.getOptional(id)
            .orElseThrow(() -> new ValueConversionException(targetType, "registry id", id,
                String.format("No object with id '%s' in registry '%s'", id, registry.key().location())));
    }
}
