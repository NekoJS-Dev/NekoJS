package com.tkisor.nekojs.core.state;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Committed backing store for a single managed top-level namespace.
 *
 * <p>Each {@code ScriptType} in a root has a private store ({@code global}), plus one shared
 * store ({@code shared}, a work name; the final public symbols are frozen by MANAGED_SURFACE
 * and this class does not carry naming). {@link GlobalStateStores} creates the store and the
 * root owns it. It persists across normal reloads, server stops and world switches, and is
 * released on root close. Generation close only clears the <b>guest values written by that
 * generation</b> (see {@link #evictGuestOwned}); it does not empty the store.
 *
 * <p>Concurrency and conflicts: each key keeps a version number and the store keeps an epoch,
 * and both are incremented on <b>committed</b> changes. A top-level set/delete/clear in a
 * candidate generation does not write directly to the store; it is recorded in
 * {@link MapWriteSet} and takes the version baseline at the first write. Before commit, a joint
 * precheck reports a conflict when another writer committed the same key, or when anything
 * committed to the store since a clear. Writes are not lost: the other committed values are
 * preserved and the candidate fails. Private keys of different types never conflict, because
 * each store keeps its own versions; competing writes to the shared store are detected
 * explicitly by this mechanism.
 *
 * <p>Lock model: all stores of this root share the single lock of {@link GlobalStateStores} and
 * every read and write completes under it. The private and shared write sets can therefore be
 * validated and published jointly in one lock acquisition, with no half commit, and an observer
 * can never see half the state.
 */
public final class GlobalStore {

    /**
     * A committed entry: the value, the generation that wrote it (null for a direct Java-side
     * write) and whether it is a guest value.
     */
    static final class Entry {
        final Object value;
        final GenerationGlobals owner;
        final boolean guest;

        Entry(Object value, GenerationGlobals owner, boolean guest) {
            this.value = value;
            this.owner = owner;
            this.guest = guest;
        }
    }

    /**
     * The top-level write set of a candidate generation.
     *
     * <ul>
     *   <li>read-your-writes: {@link #effectiveGet} looks at the write set first and then at the
     *       committed store;</li>
     *   <li>first-write baseline: the first set/delete on a key records the committed version at
     *       that moment, and clear records the store epoch at that moment (clear covers the whole
     *       store, so any later commit is a conflict);</li>
     *   <li>entries written after a clear inside the candidate count as writes made again after
     *       the clear, and entries from before the clear are dropped;</li>
     *   <li>{@link #validate} is the joint preflight; {@link #apply} publishes under the lock and
     *       bumps the versions.</li>
     * </ul>
     */
    static final class MapWriteSet {
        static final class PendingOp {
            final Object value;
            final boolean remove;
            final long baseVersion;
            final boolean guest;

            PendingOp(Object value, boolean remove, long baseVersion, boolean guest) {
                this.value = value;
                this.remove = remove;
                this.baseVersion = baseVersion;
                this.guest = guest;
            }
        }

        private final GlobalStore store;
        private final Map<String, PendingOp> ops = new LinkedHashMap<>();
        private boolean cleared;
        private long clearBaseEpoch;

        MapWriteSet(GlobalStore store) {
            this.store = store;
        }

        boolean hasWrites() {
            return cleared || !ops.isEmpty();
        }

        Object effectiveGet(String key) {
            PendingOp op = ops.get(key);
            if (op != null) {
                return op.remove ? null : op.value;
            }
            if (cleared) {
                return null;
            }
            synchronized (store.owner.lock) {
                return store.rawGet(key);
            }
        }

        boolean effectiveContainsKey(String key) {
            PendingOp op = ops.get(key);
            if (op != null) {
                return !op.remove;
            }
            if (cleared) {
                return false;
            }
            synchronized (store.owner.lock) {
                return store.values.containsKey(key);
            }
        }

        /**
         * Snapshot of the effective contents: clear semantics merged with the write-set overrides,
         * copied out under the lock.
         */
        Map<String, Object> effectiveSnapshot() {
            synchronized (store.owner.lock) {
                Map<String, Object> out = new LinkedHashMap<>();
                if (!cleared) {
                    for (Map.Entry<String, Entry> entry : store.values.entrySet()) {
                        out.put(entry.getKey(), entry.getValue().value);
                    }
                }
                for (Map.Entry<String, PendingOp> op : ops.entrySet()) {
                    if (op.getValue().remove) {
                        out.remove(op.getKey());
                    } else {
                        out.put(op.getKey(), op.getValue().value);
                    }
                }
                return out;
            }
        }

        /**
         * Top-level set: records the key in the write set. The first write records the committed
         * version baseline, and later writes reuse the same baseline.
         */
        Object set(String key, Object value) {
            synchronized (store.owner.lock) {
                Object previous = effectiveGet(key);
                long base = ops.containsKey(key) ? ops.get(key).baseVersion : store.keyVersionUnlocked(key);
                ops.put(key, new PendingOp(value, false, base, GuestValues.isGuest(value)));
                return previous;
            }
        }

        /** Top-level delete: records a remove entry in the write set and returns the effective value before the delete. */
        Object delete(String key) {
            synchronized (store.owner.lock) {
                Object previous = effectiveGet(key);
                long base = ops.containsKey(key) ? ops.get(key).baseVersion : store.keyVersionUnlocked(key);
                ops.put(key, new PendingOp(null, true, base, false));
                return previous;
            }
        }

        /**
         * Top-level clear: marks the write set cleared, records the epoch baseline and drops the
         * write-set entries made before the clear.
         */
        void markClear() {
            synchronized (store.owner.lock) {
                this.cleared = true;
                this.clearBaseEpoch = store.epochUnlocked();
                this.ops.clear();
            }
        }

        /** Fails or cancels a candidate: discards the whole write set. Idempotent; it does not touch the committed store. */
        void discardOps() {
            synchronized (store.owner.lock) {
                ops.clear();
                cleared = false;
                clearBaseEpoch = 0L;
            }
        }

        /**
         * Joint preflight: any commit by another writer after the baseline is a conflict. Committed
         * values stay exactly as they are.
         */
        boolean overlaps(MapWriteSet other) {
            return store == other.store && hasWrites() && other.hasWrites()
                    && (cleared || other.cleared || ops.keySet().stream().anyMatch(other.ops::containsKey));
        }

        void validate() {
            for (Map.Entry<String, PendingOp> entry : ops.entrySet()) {
                long current = store.keyVersionUnlocked(entry.getKey());
                if (current != entry.getValue().baseVersion) {
                    throw new GlobalStateException("global-write-conflict",
                            store.scope + " key '" + entry.getKey() + "' was committed by another writer"
                                    + " during candidate preparation (base=" + entry.getValue().baseVersion
                                    + ", current=" + current + "); candidate fails, committed value retained");
                }
            }
            if (cleared && store.epochUnlocked() != clearBaseEpoch) {
                throw new GlobalStateException("global-write-conflict",
                        store.scope + " was committed by another writer during candidate preparation"
                                + " (clear base epoch=" + clearBaseEpoch + ", current=" + store.epochUnlocked()
                                + "); candidate fails, committed values retained");
            }
        }

        /**
         * Publishes the write set. The caller must already hold the root lock and validate must have
         * passed. It clears first, then sets or deletes each key and bumps the versions as it goes.
         */
        void apply(GenerationGlobals generation) {
            if (cleared) {
                store.rawClear();
            }
            for (Map.Entry<String, PendingOp> entry : ops.entrySet()) {
                PendingOp op = entry.getValue();
                if (op.remove) {
                    store.rawRemove(entry.getKey());
                } else {
                    store.rawPut(entry.getKey(), op.value, generation, op.guest);
                }
            }
            // The write set is consumed once: publishing clears it, so hasWrites() is false afterwards
            // and a repeated publish/discard that slips in has no entries left to act on
            ops.clear();
            cleared = false;
            clearBaseEpoch = 0L;
        }
    }

    private final GlobalStateStores owner;
    private final String scope;
    private final Map<String, Entry> values = new LinkedHashMap<>();
    private final Map<String, Long> keyVersions = new HashMap<>();
    private long epoch;

    GlobalStore(GlobalStateStores owner, String scope) {
        this.owner = owner;
        this.scope = scope;
    }

    public String scope() {
        return scope;
    }

    // ---- Reading committed state (Java-side readers and view delegates; under the root lock, see the class Javadoc) ----

    public Object get(String key) {
        synchronized (owner.lock) {
            return rawGet(key);
        }
    }

    public boolean containsKey(String key) {
        synchronized (owner.lock) {
            return values.containsKey(key);
        }
    }

    public int size() {
        synchronized (owner.lock) {
            return values.size();
        }
    }

    public boolean isEmpty() {
        synchronized (owner.lock) {
            return values.isEmpty();
        }
    }

    public Set<String> keys() {
        synchronized (owner.lock) {
            return new LinkedHashSet<>(values.keySet());
        }
    }

    /**
     * Committed version of the key (0 for a key that was never written); the baseline read point
     * of the conflict preflight.
     */
    public long keyVersion(String key) {
        synchronized (owner.lock) {
            return keyVersions.getOrDefault(key, 0L);
        }
    }

    /**
     * Committed epoch of the store, incremented on every committed change; the conflict baseline
     * read point of a candidate clear.
     */
    public long epoch() {
        synchronized (owner.lock) {
            return epoch;
        }
    }

    // ---- Direct writes from Java (the public path of "other writers": plugins and tests write
    // through root.globalState()) ----

    public Object put(String key, Object value) {
        synchronized (owner.lock) {
            return rawPut(key, value, null, GuestValues.isGuest(value));
        }
    }

    public Object remove(String key) {
        synchronized (owner.lock) {
            return rawRemove(key);
        }
    }

    public void clear() {
        synchronized (owner.lock) {
            rawClear();
        }
    }

    /** Batch write from Java: a convenience method whose per-key version semantics match {@link #put}. */
    public void putAll(Map<String, ?> map) {
        if (map == null) return;
        synchronized (owner.lock) {
            for (Map.Entry<String, ?> entry : map.entrySet()) {
                rawPut(entry.getKey(), entry.getValue(), null, GuestValues.isGuest(entry.getValue()));
            }
        }
    }

    // ---- Unlocked primitives (the caller must already hold owner.lock; used by generation
    // views and the publish path) ----

    Object rawGet(String key) {
        Entry entry = values.get(key);
        return entry == null ? null : entry.value;
    }

    Set<String> keysUnlocked() {
        return new LinkedHashSet<>(values.keySet());
    }

    /** The lock of the root state domain that owns this store; views and write sets read committed state under it. */
    Object ownerLock() {
        return owner.lock;
    }

    /**
     * Direct commit for the view of an active (non-transactional) generation: publishes
     * immediately and bumps the version immediately.
     */
    Object rawPut(String key, Object value, GenerationGlobals generation, boolean guest) {
        owner.assertOpen();
        Entry previous = values.put(key, new Entry(value, generation, guest));
        bump(key);
        return previous == null ? null : previous.value;
    }

    Object rawRemove(String key) {
        owner.assertOpen();
        Entry previous = values.remove(key);
        if (previous != null) {
            bump(key);
            return previous.value;
        }
        return null;
    }

    void rawClear() {
        owner.assertOpen();
        for (String key : values.keySet()) {
            keyVersions.merge(key, 1L, Long::sum);
        }
        values.clear();
        epoch++;
    }

    long keyVersionUnlocked(String key) {
        return keyVersions.getOrDefault(key, 0L);
    }

    long epochUnlocked() {
        return epoch;
    }

    /**
     * Generation close: clears only the guest values written by that generation. Being in the map
     * does not keep a value alive forever.
     */
    void evictGuestOwned(GenerationGlobals generation) {
        if (generation == null) return;
        synchronized (owner.lock) {
            List<String> toRemove = new ArrayList<>();
            for (Map.Entry<String, Entry> entry : values.entrySet()) {
                if (generation.equals(entry.getValue().owner) && entry.getValue().guest) {
                    toRemove.add(entry.getKey());
                }
            }
            for (String key : toRemove) {
                values.remove(key);
                // Bumped as a committed change, so a candidate that is still being built detects the
                // invalidation sweep that ran after its baseline, the same way it sees any other
                // writer's commit.
                bump(key);
            }
        }
    }

    /** Root close: releases all state; afterwards the store of this root is not used any more. */
    void release() {
        synchronized (owner.lock) {
            values.clear();
            keyVersions.clear();
            epoch = 0L;
        }
    }

    private void bump(String key) {
        keyVersions.merge(key, 1L, Long::sum);
        epoch++;
    }
}
