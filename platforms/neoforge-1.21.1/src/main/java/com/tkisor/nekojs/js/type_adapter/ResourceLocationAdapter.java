package com.tkisor.nekojs.js.type_adapter;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.data.AbstractJSTypeAdapter;
import com.tkisor.nekojs.api.data.ValueConversionException;
import java.util.List;

import static com.tkisor.nekojs.api.AdapterInputShape.*;
import com.tkisor.nekojs.api.data.NekoId;
import net.minecraft.resources.ResourceLocation;

/**
 * ResourceLocation 适配器。无命名空间的 string 用 {@link NekoJS#MODID} 作为默认命名空间
 * （保留旧实现行为）。识别但非法的输入（如 tryParse 失败）抛 {@link ValueConversionException}。
 */
public class ResourceLocationAdapter extends AbstractJSTypeAdapter<ResourceLocation> {

    private static final String DEFAULT_NAMESPACE = NekoJS.MODID;

    @Override
    public Class<ResourceLocation> getTargetClass() {
        return ResourceLocation.class;
    }

    @Override
    public List<AdapterInputShape> inputShapes() {
        return List.of(
                self(),
                string(),
                host(NekoId.class));
    }

    @Override
    protected ResourceLocation fromString(String s) {
        if (s.contains(":")) {
            // 1.21.1: ResourceLocation.parse 在非法时直接抛异常；先 tryParse 做 null 检查统一错误。
            ResourceLocation parsed = ResourceLocation.tryParse(s);
            if (parsed == null) {
                throw new ValueConversionException(ResourceLocation.class, "valid resource location string", s,
                    "invalid resource location: " + s);
            }
            return parsed;
        }
        return ResourceLocation.fromNamespaceAndPath(DEFAULT_NAMESPACE, s);
    }

    @Override
    protected ResourceLocation fromHostObject(Object host) {
        if (host instanceof ResourceLocation location) return location;
        if (host instanceof NekoId id) return ResourceLocation.fromNamespaceAndPath(id.namespace(), id.path());
        return null;
    }
}
