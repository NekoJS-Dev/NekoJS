package com.tkisor.nekojs.core.lifecycle;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Core marker table behind the run-once guard (the {@code once} / {@code clearOnce} bindings).
 *
 * <p>Markers <b>survive a script reload</b> on purpose. They live in process memory and are not
 * persisted: a reload rebuilds the Context, but one-time work such as init probes and migrations
 * must not run again because of it. Call {@code clearOnce(key)} to remove one key, or
 * {@code clearOnce()} to clear them all.
 *
 * <p>{@link #runOnce(String)} implements check-and-set with the atomic add of
 * {@link ConcurrentHashMap#newKeySet()}. When several Contexts call it concurrently with the same
 * key, exactly one call returns true.
 */
public final class OnceRegistry {

    /** Shared instance for every ScriptType's {@code once} binding; markers live for the whole process. */
    public static final OnceRegistry SHARED = new OnceRegistry();

    private final Set<String> markers = ConcurrentHashMap.newKeySet();

    /**
     * Atomic check-and-set: the first call with a key records the marker and returns {@code true}, so
     * the caller runs the callback. Every later call returns {@code false}.
     */
    public boolean runOnce(String key) {
        return markers.add(resolveKey(key));
    }

    /** Returns whether the marker for this key exists, meaning {@link #runOnce} already returned true for it. */
    public boolean has(String key) {
        return markers.contains(resolveKey(key));
    }

    /** Removes the marker for one key, so the next {@link #runOnce} returns true again. Returns whether it existed. */
    public boolean clear(String key) {
        return markers.remove(resolveKey(key));
    }

    /** Clears every marker, so all keys return true again on their next {@link #runOnce} call. */
    public void clearAll() {
        markers.clear();
    }

    /** Number of markers currently held. */
    public int size() {
        return markers.size();
    }

    /**
     * Resolves a key. In v1 keys are global, so this returns the key unchanged.
     *
     * <p>To isolate keys per script owner later, so the same key in different scripts does not collide,
     * prefix the current script id here. The binding layer and {@link #clear}/{@link #has} need no
     * change.
     */
    public String resolveKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("once() key must be a non-blank string");
        }
        // TODO(per-script-namespacing): prefix with the owning script id, e.g. scriptId + ':' + key
        return key;
    }
}
