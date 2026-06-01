package com.tkisor.nekojs.js.type_adapter;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.api.JSTypeAdapter;
import com.tkisor.nekojs.api.data.NekoId;
import graal.graalvm.polyglot.Value;
import net.minecraft.resources.ResourceLocation;

public class ResourceLocationAdapter implements JSTypeAdapter<ResourceLocation> {

    private static final String DEFAULT_NAMESPACE = NekoJS.MODID;

    @Override
    public Class<ResourceLocation> getTargetClass() {
        return ResourceLocation.class;
    }

    @Override
    public boolean test(Value value) {
        return value.isString() || value.isHostObject() && value.asHostObject() instanceof NekoId;
    }

    @Override
    public ResourceLocation apply(Value value) {
        if (value.isHostObject() && value.asHostObject() instanceof NekoId id) {
            return ResourceLocation.fromNamespaceAndPath(id.namespace(), id.path());
        }

        String id = value.asString();
        if (id.contains(":")) {
            return ResourceLocation.parse(id);
        }
        return ResourceLocation.fromNamespaceAndPath(DEFAULT_NAMESPACE, id);
    }
}
