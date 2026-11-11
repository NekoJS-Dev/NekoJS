//~ mc_legacy_api
package com.tkisor.nekojs.platform;

import com.tkisor.nekojs.api.data.NekoId;
import net.minecraft.resources.Identifier;

public final class NeoForgeIdCompat implements NekoIdCompat.Adapter {
    @Override
    public Class<?> platformIdType() { return Identifier.class; }

    @Override
    public NekoId fromPlatformId(Object id) {
        if (!(id instanceof Identifier nativeId)) {
            throw new IllegalArgumentException("[NEKO-4056] Expected a native resource ID");
        }
        return NekoId.resource(nativeId.getNamespace(), nativeId.getPath());
    }

    @Override
    public Identifier toPlatformId(NekoId id) {
        return Identifier.fromNamespaceAndPath(id.namespace(), id.path());
    }
}
