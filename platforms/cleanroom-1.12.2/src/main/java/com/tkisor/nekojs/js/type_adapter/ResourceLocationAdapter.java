package com.tkisor.nekojs.js.type_adapter;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.data.AbstractJSTypeAdapter;
import com.tkisor.nekojs.api.data.ValueConversionException;
import java.util.List;

import static com.tkisor.nekojs.api.AdapterInputShape.*;
import com.tkisor.nekojs.api.data.NekoId;
import net.minecraft.util.ResourceLocation;

/**
 * ResourceLocation 适配器（1.12.2 版）。无命名空间的 string 用 {@link NekoJS#MODID} 作为默认命名空间。
 * 非法输入抛 {@link ValueConversionException}。
 *
 * <p>1.12.2 适配：使用 try-catch 包裹 {@code new ResourceLocation(id)} 替代 tryParse，
 * 使用 {@code new ResourceLocation(ns, path)} 替代 fromNamespaceAndPath。</p>
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
            try {
                return new ResourceLocation(s);
            } catch (Exception e) {
                throw new ValueConversionException(ResourceLocation.class, "valid resource location string", s,
                    "invalid resource location: " + s);
            }
        }
        return new ResourceLocation(DEFAULT_NAMESPACE, s);
    }

    @Override
    protected ResourceLocation fromHostObject(Object host) {
        if (host instanceof ResourceLocation location) return location;
        if (host instanceof NekoId id) return new ResourceLocation(id.namespace(), id.path());
        return null;
    }
}
