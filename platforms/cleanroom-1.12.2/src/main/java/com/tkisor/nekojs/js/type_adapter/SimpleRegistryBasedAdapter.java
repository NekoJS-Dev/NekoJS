package com.tkisor.nekojs.js.type_adapter;

import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.data.AbstractJSTypeAdapter;
import com.tkisor.nekojs.api.data.NekoId;
import com.tkisor.nekojs.api.data.ValueConversionException;
import java.util.List;
import java.util.function.Function;
import net.minecraft.util.ResourceLocation;

import static com.tkisor.nekojs.api.AdapterInputShape.*;

/**
 * 1.12.2 通用注册表适配器，服务于<b>纯 id 查询、无跨类型转换</b>的注册表类型。
 *
 * <p>1.12.2 无统一 {@code Registry<T>} 接口，改用 {@code Function<ResourceLocation, T>} 作为注册表查找函数。
 * {@code registryName} 用于 probe {@code registry(...)} 形状（生成 {@code $MobEffect_} 别名）。
 *
 * <p>能力：string id（自动补 {@code minecraft:} 前缀） / {@link NekoId} / {@link ResourceLocation} /
 * 自身类型 host。无跨类型转换、无对象/数组语法 —— 需要这些的类型请手写 {@link AbstractJSTypeAdapter} 子类。
 */
public class SimpleRegistryBasedAdapter<T> extends AbstractJSTypeAdapter<T> {
    private final Function<ResourceLocation, T> registryLookup;
    private final Class<T> targetType;
    private final String registryName;

    /**
     * @param registryLookup 注册表查找函数，接受 ResourceLocation 返回 T，找不到应返回 null
     * @param targetType     目标类型
     * @param registryName   注册表名称（用于 probe 形状生成）
     */
    public SimpleRegistryBasedAdapter(Function<ResourceLocation, T> registryLookup, Class<T> targetType, String registryName) {
        this.registryLookup = registryLookup;
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
            return getFromRegistry(new ResourceLocation(namespace, path));
        }
        if (host instanceof ResourceLocation location) {
            return getFromRegistry(location);
        }
        if (targetType.isInstance(host)) {
            return targetType.cast(host);
        }
        return null;
    }

    private T getFromRegistry(ResourceLocation id) {
        T value = registryLookup.apply(id);
        if (value == null) {
            throw new ValueConversionException(targetType, "registered " + targetType.getSimpleName() + " id",
                id, "no object with id '" + id + "' in registry '" + registryName + "'");
        }
        return value;
    }

    private ResourceLocation parseId(String rawId) {
        if (rawId == null || rawId.trim().isEmpty()) {
            throw new ValueConversionException(targetType, "registry id string", rawId,
                "empty " + targetType.getSimpleName() + " id");
        }
        String id = rawId.trim();
        if (!id.contains(":")) id = "minecraft:" + id;
        try {
            return new ResourceLocation(id);
        } catch (Exception e) {
            throw new ValueConversionException(targetType, "valid registry id", rawId,
                "invalid id syntax: " + rawId);
        }
    }
}
