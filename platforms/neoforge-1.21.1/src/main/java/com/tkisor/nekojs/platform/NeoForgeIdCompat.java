package com.tkisor.nekojs.platform;

import com.tkisor.nekojs.api.data.NekoId;
import net.minecraft.resources.ResourceLocation;

public final class NeoForgeIdCompat implements NekoIdCompat.Adapter {
    @Override
    public ResourceLocation toPlatformId(NekoId id) {
        return ResourceLocation.fromNamespaceAndPath(id.namespace(), id.path());
    }
}
