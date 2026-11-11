//~ mc_legacy_api
package com.tkisor.nekojs.bindings.static_access;

import com.tkisor.nekojs.api.data.NekoId;
import com.tkisor.nekojs.platform.NekoIdCompat;
import net.minecraft.resources.Identifier;

/** Explicit native interoperation; native members remain profile-dependent. */
@com.tkisor.nekojs.api.annotation.ScriptNativeBridge
public final class NativeBridgeJS {
    private final Ids ids = new Ids();

    public Ids getIds() { return ids; }

    public static final class Ids {
        public Identifier toNative(NekoId id) {
            if (id == null) throw new IllegalArgumentException("[NEKO-4056] Resource ID cannot be null");
            return Identifier.class.cast(NekoIdCompat.toPlatformId(id));
        }

        public NekoId fromNative(Identifier id) {
            return NekoIdCompat.fromPlatformId(id);
        }
    }
}
