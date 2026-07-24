package com.tkisor.nekojs.platform;

import com.tkisor.nekojs.api.data.NekoId;
import net.minecraft.resources.Identifier;

public final class NeoForgeIdCompat implements NekoIdCompat.Adapter {
    @Override
    public Identifier toPlatformId(NekoId id) {
        return Identifier.fromNamespaceAndPath(id.namespace(), id.path());
    }
}
