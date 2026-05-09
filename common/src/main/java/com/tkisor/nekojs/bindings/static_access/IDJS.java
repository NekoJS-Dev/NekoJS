package com.tkisor.nekojs.bindings.static_access;

import com.tkisor.nekojs.api.data.NekoId;
import com.tkisor.nekojs.platform.NekoIdCompat;

public final class IDJS {
    public NekoId of(String value) {
        return NekoId.of(value);
    }

    public NekoId of(String namespace, String path) {
        return NekoId.of(namespace, path);
    }

    public String namespace(NekoId id) {
        return id.namespace();
    }

    public String path(NekoId id) {
        return id.path();
    }

    public String asString(NekoId id) {
        return id.asString();
    }

    public Object platform(NekoId id) {
        return NekoIdCompat.toPlatformId(id);
    }
}
