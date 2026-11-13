package com.tkisor.nekojs.platform;

import com.tkisor.nekojs.api.data.NekoId;
import net.minecraft.resources.Identifier;

/**
 * Fabric-side {@link NekoIdCompat.Adapter}: it only touches vanilla API and matches the NeoForge
 * side.
 *
 * <p>The vanilla class is {@code Identifier} from 26.1 on (it was {@code ResourceLocation} in
 * 1.21.x). The Fabric branch currently has a single node, 26.1.2, so the 26.x form is written
 * directly. When a 1.21.1 node is added, the controller's `mc_renames` replacements handle this
 * spot with the same rules as the NeoForge side.
 */
public final class FabricIdCompat implements NekoIdCompat.Adapter {
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
