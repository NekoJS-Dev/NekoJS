package com.tkisor.nekojs.platform;

import com.tkisor.nekojs.api.data.NekoId;
import net.minecraft.util.ResourceLocation;

public final class ForgeIdCompat implements NekoIdCompat.Adapter {
    @Override
    public ResourceLocation toPlatformId(NekoId id) {
        return new ResourceLocation(id.namespace(), id.path());
    }
}
