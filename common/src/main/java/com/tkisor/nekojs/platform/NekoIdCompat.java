package com.tkisor.nekojs.platform;

import com.tkisor.nekojs.api.annotation.HideFromJS;
import com.tkisor.nekojs.api.data.NekoId;

public final class NekoIdCompat {
    private static Adapter ADAPTER;

    private NekoIdCompat() {}

    @HideFromJS
    public static void init(Adapter adapter) {
        if (ADAPTER != null) {
            throw new IllegalStateException("NekoIdCompat has already been initialized");
        }
        if (adapter == null) {
            throw new NullPointerException("NekoIdCompat adapter cannot be null");
        }
        ADAPTER = adapter;
    }

    @HideFromJS
    public static Object toPlatformId(NekoId id) {
        return get().toPlatformId(id);
    }

    private static Adapter get() {
        if (ADAPTER == null) {
            throw new IllegalStateException("NekoIdCompat has not been initialized");
        }
        return ADAPTER;
    }

    public interface Adapter {
        Object toPlatformId(NekoId id);
    }
}
