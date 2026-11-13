package com.tkisor.nekojs.api.data;

import java.util.HashMap;

/**
 * Small in-memory state container attached to a host such as {@code MinecraftServer},
 * {@code Level} or {@code Player}.
 *
 * <p>This is the equivalent of KubeJS {@code AttachedData}: purely in memory, never persisted
 * (use the existing {@code pdata}/{@code PersistentDataJS} when state must survive a restart).
 * Scripts and plugins reach it through {@code host.data}. The platform mixin creates it
 * lazily on first access, then walks every plugin and fires the matching {@code attachXxxData}
 * hook.
 *
 * <p>The API is method-based, like {@code PersistentDataJS}: {@code data.get("k")} /
 * {@code data.put("k", v)} / {@code data.add("k", v)} / {@code data.containsKey("k")} /
 * {@code data.remove("k")}.
 *
 * @param <T> host type
 */
public class AttachedData<T> extends HashMap<String, Object> {
    private static final long serialVersionUID = 1L;

    // The host object is not serializable (MinecraftServer/Level/Player and the like) and this
    // container is in-memory only, but the field stays non-transient so serialization cannot
    // silently drop the reference
    @SuppressWarnings("serial")
    private final T parent;

    public AttachedData(T parent) {
        this.parent = parent;
    }

    /** Returns the host object this container is attached to. */
    public T getParent() {
        return parent;
    }

    /** Alias for {@link #put(Object, Object)} that reads better from scripts. */
    public void add(String key, Object data) {
        put(key, data);
    }
}
