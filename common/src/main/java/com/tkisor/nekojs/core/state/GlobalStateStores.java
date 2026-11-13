package com.tkisor.nekojs.core.state;

import com.tkisor.nekojs.api.ScriptType;

import java.util.EnumMap;

/**
 * Managed global/shared state domains owned by the root; replaces the process-wide static maps
 * of {@code NekoGlobal}.
 *
 * <ul>
 *   <li><b>Private store per type</b>: one store for each {@code ScriptType}, so a key with the
 *       same name in STARTUP/SERVER/CLIENT/TEST never overwrites another. Several files of one
 *       type and an ordinary reload of that type share that store.</li>
 *   <li><b>Explicit shared store</b>: an entry point for explicit sharing across types (work
 *       name {@code shared}; the final public symbols are frozen by MANAGED_SURFACE). It keeps
 *       the same narrow-map semantics.</li>
 *   <li><b>Lifetime</b>: the stores survive ordinary reloads, server stop and world switches
 *       within the same root. {@link #closeAll()} (root close) releases them. Generation close
 *       does not empty a store; it only invalidates that generation's guest values. A new
 *       independent root or test runner starts from empty state, since this class holds no
 *       static state.</li>
 *   <li><b>Sharing memory is not network sync</b>: the shared store is shared only inside the
 *       process of one root and does not replace the client/server network protocol.</li>
 * </ul>
 *
 * <p>This class contains no domain semantics. A domain plan joins the joint
 * preflight/joint outcome path of {@link GenerationGlobals} through {@link CandidateStatePlan}
 * and never enters the global owner.
 */
public final class GlobalStateStores {

    /**
     * The lock shared by the whole root state domain. Every store read/write and every joint
     * publish runs under it (see GlobalStore).
     */
    final Object lock = new Object();

    private final EnumMap<ScriptType, GlobalStore> privateStores = new EnumMap<>(ScriptType.class);
    private final java.util.Map<String, GlobalStore> packStores = new java.util.LinkedHashMap<>();
    private final GlobalStore sharedStore = new GlobalStore(this, "shared");
    private volatile boolean closed;

    /** The private global store of this type, created on demand; generations of the same type share it. */
    public GlobalStore storeFor(ScriptType type) {
        assertOpen();
        synchronized (lock) {
            return privateStores.computeIfAbsent(type, t -> new GlobalStore(this, "global:" + t.name));
        }
    }

    /** The explicit cross-type shared store (work name shared). */
    public GlobalStore sharedStore() {
        assertOpen();
        return sharedStore;
    }

    /**
     * Creates the holder of a generation's global/shared views.
     *
     * @param transactional {@code true} creates a candidate generation (top-level writes go into
     *                      the write set and are published only on commit); {@code false} creates
     *                      an active, non-transactional generation (top-level writes commit
     *                      directly)
     */
    public GenerationGlobals newGeneration(ScriptType type, boolean transactional) {
        assertOpen();
        return new GenerationGlobals(this, type, transactional);
    }

    /** Opt-in pack state is isolated by source instance and type; shared retains its root owner. */
    public GenerationGlobals newPackGeneration(ScriptType type, String instance, boolean transactional) {
        synchronized (lock) {
            assertOpen();
            GlobalStore store = packStores.computeIfAbsent(type.name + ":" + instance,
                    key -> new GlobalStore(this, "pack:" + key));
            return new GenerationGlobals(this, type, transactional, store);
        }
    }

    /** World and connection teardown release their private state after borrowers stop. */
    public void releasePackScope(String scope) {
        synchronized (lock) {
            packStores.entrySet().removeIf(entry -> {
                if (!entry.getKey().contains(":" + scope + ":")) return false;
                entry.getValue().release(); return true;
            });
        }
    }

    /** Root close: releases all state. Afterwards every write entry point of these stores is rejected. */
    public void closeAll() {
        closed = true;
        synchronized (lock) {
            for (GlobalStore store : privateStores.values()) {
                store.release();
            }
            privateStores.clear();
            packStores.values().forEach(GlobalStore::release);
            packStores.clear();
            sharedStore.release();
        }
    }

    /** Test/diagnostic probe: whether the stores were already released by root close. */
    public boolean isClosed() {
        return closed;
    }

    void assertOpen() {
        if (closed) {
            throw new IllegalStateException("GlobalStateStores already released by root close");
        }
    }
}
