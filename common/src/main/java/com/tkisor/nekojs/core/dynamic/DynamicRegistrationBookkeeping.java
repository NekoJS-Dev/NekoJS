package com.tkisor.nekojs.core.dynamic;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Platform-free ownership bookkeeping for runtime (post-server-start) registry
 * registrations: which script claimed which entry, in which mode, and which
 * claims have gone stale after a reload.
 *
 * <p>Model (mirrors the Katton stale-retain finding): reload NEVER unregisters
 * entries mid-session. Instead, {@link #beginReload()} flips every live claim to
 * stale; scripts that re-run re-claim their ids via {@link #claim}, which clears
 * the stale flag. Anything still stale after scripts finished loading belongs to
 * a script that no longer registers it — surfaced through {@link #staleIds()}
 * for the {@code /nekojs registry} debug view.
 *
 * <p>Entries are keyed by plain strings (registry label + entry id) so this class
 * stays testable without Minecraft classes; the platform layer owns the actual
 * registry surgery.
 *
 * <p>Thread-safety: all mutating access is {@code synchronized}; script load
 * (claiming) runs on the script thread while the debug command reads snapshots
 * from the server thread.
 */
public final class DynamicRegistrationBookkeeping {

    /** One tracked registration claim. */
    public static final class Entry {
        private final String registry;
        private final String id;
        private String ownerScriptId;
        private DynamicRegisterMode mode;
        private boolean stale;

        private Entry(String registry, String id, String ownerScriptId, DynamicRegisterMode mode) {
            this.registry = registry;
            this.id = id;
            this.ownerScriptId = ownerScriptId;
            this.mode = mode;
        }

        public String registry() {
            return registry;
        }

        public String id() {
            return id;
        }

        /** Script id of the last claim, e.g. {@code nekojs:server/packs/foo/main.js}. */
        public String ownerScriptId() {
            return ownerScriptId;
        }

        public DynamicRegisterMode mode() {
            return mode;
        }

        /** True when the claim was not re-made after the most recent {@link #beginReload}. */
        public boolean stale() {
            return stale;
        }

        boolean ownerEquals(String other) {
            return Objects.equals(ownerScriptId, other);
        }
    }

    private final String registry;
    private final Map<String, Entry> entries = new LinkedHashMap<>();

    public DynamicRegistrationBookkeeping(String registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    public String registry() {
        return registry;
    }

    /**
     * Creates a new claim or re-claims an existing one (clearing its stale flag).
     * Re-claiming under a different owner updates the owner with no error — reloads
     * may legitimately move an id from one script to another.
     *
     * @throws NullPointerException when {@code mode} is null (script-facing layers
     *         must translate this into a clear script error).
     */
    public synchronized void claim(String id, String ownerScriptId, DynamicRegisterMode mode) {
        Objects.requireNonNull(mode, "mode");
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Missing entry id");
        }
        String owner = ownerScriptId == null || ownerScriptId.isBlank() ? "<unknown>" : ownerScriptId;
        Entry entry = entries.get(id);
        if (entry == null) {
            entries.put(id, new Entry(registry, id, owner, mode));
        } else {
            entry.ownerScriptId = owner;
            entry.mode = mode;
            entry.stale = false;
        }
    }

    /**
     * Reload boundary: marks every live claim stale. Entries stay registered and
     * tracked (stale-retain); claims that come back clear their own flag.
     */
    public synchronized void beginReload() {
        for (Entry entry : entries.values()) {
            entry.stale = true;
        }
    }

    /** Drops a claim entirely (used by the world-leave cleanup path). */
    public synchronized void remove(String id) {
        entries.remove(id);
    }

    /** Number of tracked claims (stale included). */
    public synchronized int count() {
        return entries.size();
    }

    /** Whether {@code id} is tracked here (i.e. was registered through this system). */
    public synchronized boolean isTracked(String id) {
        return entries.containsKey(id);
    }

    /** Tracked claim for {@code id}, or null. */
    public synchronized Entry entry(String id) {
        return entries.get(id);
    }

    /** Ids whose claims were not re-made after the most recent {@link #beginReload}. */
    public synchronized List<String> staleIds() {
        List<String> stale = new ArrayList<>();
        for (Entry entry : entries.values()) {
            if (entry.stale) {
                stale.add(entry.id);
            }
        }
        return stale;
    }

    /** All tracked ids (stale included), in claim order. */
    public synchronized Set<String> trackedIds() {
        return new LinkedHashSet<>(entries.keySet());
    }

    /** Tracked ids registered under {@code mode} (stale included). */
    public synchronized List<String> idsByMode(DynamicRegisterMode mode) {
        Objects.requireNonNull(mode, "mode");
        List<String> ids = new ArrayList<>();
        for (Entry entry : entries.values()) {
            if (entry.mode == mode) {
                ids.add(entry.id);
            }
        }
        return ids;
    }

    /** Copy of all claims for debug output. */
    public synchronized List<Entry> snapshot() {
        return new ArrayList<>(entries.values());
    }
}
