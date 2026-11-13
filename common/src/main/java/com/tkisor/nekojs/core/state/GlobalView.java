package com.tkisor.nekojs.core.state;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * The {@code global}/{@code shared} view bound to a single Context.
 *
 * <p>The script-facing surface matches the host {@link Map} binding it replaces: {@code global.foo}
 * reads a member, {@code global.foo = v} writes one, {@code delete global.foo} removes one and
 * {@code global.clear()} empties the view. GraalJS member access on a host {@link Map} is
 * get/put/remove. This class implements {@link Map} to keep that interop semantics: a member key
 * resolves to the Map interface method names, the same behavior the previous
 * ConcurrentHashMap binding had.
 *
 * <p>For a transactional (candidate) generation, top-level set/delete/clear go into
 * {@link GlobalStore.MapWriteSet}, which gives read-your-writes, discards the writes on failure
 * and publishes them jointly on commit. For a non-transactional generation they commit into the
 * store directly. <b>No deep rollback is promised</b>: changes made inside nested objects,
 * lists or already shared Java objects stay outside the write-set transaction. A script that
 * needs transaction protection should build a new value and replace the top-level key with it.
 */
public final class GlobalView implements Map<String, Object> {

    private final GenerationGlobals generation;
    private final GlobalStore store;
    private final GlobalStore.MapWriteSet writes; // null = non-transactional (commits directly)

    GlobalView(GenerationGlobals generation, GlobalStore store, GlobalStore.MapWriteSet writes) {
        this.generation = generation;
        this.store = store;
        this.writes = writes;
    }

    /** The namespace this view is bound to ({@code "global:<type>"} or {@code "shared"}); used for diagnostics. */
    public String scope() {
        return store.scope();
    }

    // ---- Top-level writes (the entry point of the transaction semantics) ----

    @Override
    public Object put(String key, Object value) {
        if (writes != null && generation.isTransactional()) {
            return writes.set(key, value);
        }
        synchronized (store.ownerLock()) {
            return store.rawPut(key, value, generation, GuestValues.isGuest(value));
        }
    }

    @Override
    public Object remove(Object key) {
        if (!(key instanceof String stringKey)) return null;
        if (writes != null && generation.isTransactional()) {
            return writes.delete(stringKey);
        }
        synchronized (store.ownerLock()) {
            return store.rawRemove(stringKey);
        }
    }

    @Override
    public void clear() {
        if (writes != null && generation.isTransactional()) {
            writes.markClear();
            return;
        }
        synchronized (store.ownerLock()) {
            store.rawClear();
        }
    }

    // ---- Reads (read-your-writes: a candidate checks the write set before the committed store) ----

    @Override
    public Object get(Object key) {
        if (!(key instanceof String stringKey)) return null;
        if (writes != null && generation.isTransactional()) {
            return writes.effectiveGet(stringKey);
        }
        return store.get(stringKey);
    }

    @Override
    public boolean containsKey(Object key) {
        if (!(key instanceof String stringKey)) return false;
        if (writes != null && generation.isTransactional()) {
            return writes.effectiveContainsKey(stringKey);
        }
        return store.containsKey(stringKey);
    }

    @Override
    public int size() {
        return effectiveContents().size();
    }

    @Override
    public boolean isEmpty() {
        return effectiveContents().isEmpty();
    }

    // ---- The rest of the Map surface (snapshot semantics; interop only uses get/put/remove/clear,
    // these exist for Java callers) ----

    @Override
    public Set<String> keySet() {
        return new LinkedHashSet<>(effectiveContents().keySet());
    }

    @Override
    public Collection<Object> values() {
        return effectiveContents().values();
    }

    @Override
    public Set<Map.Entry<String, Object>> entrySet() {
        return effectiveContents().entrySet();
    }

    @Override
    public void putAll(Map<? extends String, ?> m) {
        if (m == null) return;
        for (Map.Entry<? extends String, ?> entry : m.entrySet()) {
            put(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public boolean containsValue(Object value) {
        return effectiveContents().containsValue(value);
    }

    /**
     * Snapshot of the effective contents: the write-set overrides merged with the clear semantics,
     * copied out under the lock so no internal structure leaks.
     */
    private Map<String, Object> effectiveContents() {
        if (writes != null && generation.isTransactional()) {
            return writes.effectiveSnapshot();
        }
        synchronized (store.ownerLock()) {
            Map<String, Object> out = new HashMap<>();
            for (String key : store.keysUnlocked()) {
                out.put(key, store.rawGet(key));
            }
            return out;
        }
    }
}
