package com.tkisor.nekojs.js.type_adapter;

import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.data.AbstractJSTypeAdapter;
import com.tkisor.nekojs.api.data.NekoId;
import com.tkisor.nekojs.api.data.ValueConversionException;
import java.util.List;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;

import static com.tkisor.nekojs.api.AdapterInputShape.*;

/**
 * 基于 {@link Registry} 的通用适配器，服务于<b>纯 id 查询、无跨类型转换</b>的注册表类型
 * （{@code MobEffect}、{@code Potion}、{@code SoundEvent} 等）。
 *
 * <p>构造形态：{@code new SimpleRegistryBasedAdapter<>(registry, type, registryName)}。
 * {@code registryName} 用于 probe {@code registry(...)} 形状（生成 {@code $MobEffect_} 别名）。
 *
 * <p>能力：string id（自动补 {@code minecraft:} 前缀） / {@link NekoId} / {@link Identifier} /
 * 自身类型 host。无跨类型转换、无对象/数组语法 —— 需要这些的类型（Item/Block 互转、
 * Ingredient 前缀分派）请手写 {@link AbstractJSTypeAdapter} 子类，不要硬塞进本类。
 *
 * @author ZZZank
 */
public class SimpleRegistryBasedAdapter<T> extends AbstractJSTypeAdapter<T> {
    private final Registry<T> registry;
    private final Class<T> targetType;
    private final String registryName;

    public SimpleRegistryBasedAdapter(Registry<T> registry, Class<T> targetType, String registryName) {
        this.registry = registry;
        this.targetType = targetType;
        this.registryName = registryName;
    }

    @Override
    public Class<T> getTargetClass() {
        return targetType;
    }

    @Override
    public List<AdapterInputShape> inputShapes() {
        return List.of(
                self(),
                registry(registryName),
                host(NekoId.class),
                string());
    }

    @Override
    protected T fromString(String rawId) {
        return getFromRegistry(parseId(rawId));
    }

    @Override
    protected T fromHostObject(Object host) {
        if (host instanceof NekoId(String namespace, String path)) {
            return getFromRegistry(Identifier.fromNamespaceAndPath(namespace, path));
        }
        if (host instanceof Identifier identifier) {
            return getFromRegistry(identifier);
        }
        if (targetType.isInstance(host)) {
            return targetType.cast(host);
        }
        return null; // 不识别
    }

    private T getFromRegistry(Identifier id) {
        return registry.getOptional(id)
            .orElseThrow(() -> new ValueConversionException(targetType, "registered " + targetType.getSimpleName() + " id",
                id, "no object with id '" + id + "' in registry '" + registry.key().identifier() + "'"));
    }

    private Identifier parseId(String rawId) {
        if (rawId == null || rawId.isBlank()) {
            throw new ValueConversionException(targetType, "registry id string", rawId,
                "empty " + targetType.getSimpleName() + " id");
        }
        String id = rawId.trim();
        if (!id.contains(":")) id = "minecraft:" + id;
        Identifier location = Identifier.tryParse(id);
        if (location == null) {
            throw new ValueConversionException(targetType, "valid registry id", rawId,
                "invalid id syntax: " + rawId);
        }
        return location;
    }
}