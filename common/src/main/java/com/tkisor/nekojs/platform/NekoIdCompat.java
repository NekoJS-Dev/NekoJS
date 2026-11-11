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
        if (id == null) throw new IllegalArgumentException("[NEKO-4056] Resource ID cannot be null");
        return get().toPlatformId(NekoId.resource(id.namespace(), id.path()));
    }

    @HideFromJS
    public static NekoId fromPlatformId(Object id) {
        NekoId value = get().fromPlatformId(id);
        return NekoId.resource(value.namespace(), value.path());
    }

    @HideFromJS
    public static Class<?> platformIdType() {
        return get().platformIdType();
    }

    private static Adapter get() {
        if (ADAPTER == null) {
            throw new IllegalStateException("NekoIdCompat has not been initialized");
        }
        return ADAPTER;
    }

    public interface Adapter {
        Object toPlatformId(NekoId id);

        default NekoId fromPlatformId(Object id) {
            throw new IllegalArgumentException("[NEKO-4056] Native ID conversion is not supported by this adapter");
        }

        default Class<?> platformIdType() { return Object.class; }
    }
}
