package com.tkisor.nekojs.core.villager;

import graal.graalvm.polyglot.Context;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Bookkeeping of the villager trade domain (ticket 22 AC3/AC7): the committed generation
 * number, the snapshot the last commit published and the stale/retired records ordinary
 * reloads must keep instead of deleting registry entries.
 *
 * <p>Ownership: one instance per platform assembly, held by the root-registered domain owner
 * (see {@code CandidateDomainCollector}) and exposed to the {@code VillagerTrades} query
 * binding through the process-level facade. It is not a runtime owner: no context, no
 * listeners, no lifecycle of its own — the root closes the owner that holds it.
 *
 * <p>Every read goes through {@link #query(Context)}: the reading generation is resolved from
 * the calling script Context (candidate, superseded, killed or closed generations get an
 * explicit {@link VillagerTradeQuerySurface.Status#STALE} answer).
 */
public final class VillagerTradeDomainState {

    private final String adapterId;
    private final boolean available;

    private long committedGeneration;
    private VillagerTradeSetSnapshot committedSnapshot = VillagerTradeSetSnapshot.EMPTY;
    private final List<String> unrestoredKeys = new ArrayList<>();
    private final Set<String> retiredKeys = new LinkedHashSet<>();

    public VillagerTradeDomainState(String adapterId, boolean available) {
        this.adapterId = adapterId;
        this.available = available;
    }

    /** Adapter node id that owns this state ({@code "unavailable"} when the node has none). */
    public String adapterId() {
        return adapterId;
    }

    /** Whether the node behind this state can perform registry mutation at all. */
    public boolean available() {
        return available;
    }

    /** Whether this domain ever committed a batch (used to decide zero-participation reloads). */
    public synchronized boolean hasCommittedRecords() {
        return committedGeneration > 0L;
    }

    /** Generation number of the last commit ({@code 0} when nothing was committed yet). */
    public synchronized long committedGeneration() {
        return committedGeneration;
    }

    public synchronized VillagerTradeSetSnapshot committedSnapshot() {
        return committedSnapshot;
    }

    /**
     * Commit bookkeeping: advances the domain generation and records which previously declared
     * listings the new generation does not declare any more (they stay active in the registry
     * and are reported as unrestored) plus which listings the batch retired explicitly.
     */
    public synchronized void noteCommitted(VillagerTradeCandidatePlan plan, VillagerTradeSetSnapshot snapshot) {
        Map<String, List<String>> declared = plan.declaredKeysByTradeSet();
        Set<String> declaredKeys = new LinkedHashSet<>();
        declared.values().forEach(declaredKeys::addAll);
        Set<String> previouslyDeclared = new LinkedHashSet<>(acceptedDeclaredKeys);

        List<String> stillUnrestored = new ArrayList<>(unrestoredKeys);
        for (String key : previouslyDeclared) {
            if (!declaredKeys.contains(key) && !stillUnrestored.contains(key)) {
                stillUnrestored.add(key);
            }
        }
        for (String key : declaredKeys) {
            stillUnrestored.remove(key); // re-declared listings leave the unrestored record
        }
        Set<String> obsoleteSets = plan.obsoleteTradeSets();
        for (String key : List.copyOf(retiredKeys)) {
            if (declaredKeys.contains(key)) {
                retiredKeys.remove(key); // re-declared: active again
            }
        }
        for (String key : previouslyDeclared) {
            if (!declaredKeys.contains(key) && obsoleteSets.contains(tradeSetOf(key))) {
                retiredKeys.add(key);
                stillUnrestored.remove(key);
            }
        }

        this.committedGeneration++;
        this.committedSnapshot = snapshot;
        this.acceptedDeclaredKeys.clear();
        this.acceptedDeclaredKeys.addAll(declaredKeys);
        this.unrestoredKeys.clear();
        this.unrestoredKeys.addAll(stillUnrestored);
    }

    /**
     * Generation-bound read-only query. {@code context} is the script Context the binding was
     * called from; an unknown, candidate, superseded, killed or closed generation yields an
     * explicit stale result — the old generation token never reads the new generation's data
     * and never gets a writable object.
     */
    public VillagerTradeQuerySurface query(Context context) {
        long generation = context == null ? -1L
                : com.tkisor.nekojs.script.ScriptManager.activeGenerationOf(context);
        if (generation < 0L) {
            return VillagerTradeQuerySurface.stale(
                    context == null ? "no-script-context" : "generation-not-active", -1L);
        }
        if (!available) {
            return VillagerTradeQuerySurface.stale("node-unavailable:" + adapterId, generation);
        }
        synchronized (this) {
            if (committedGeneration == 0L) {
                return VillagerTradeQuerySurface.stale("no-commit-yet", generation);
            }
            return VillagerTradeQuerySurface.active(generation, adapterId, committedSnapshot,
                    unrestoredKeys, List.copyOf(retiredKeys));
        }
    }

    private static String tradeSetOf(String listingKey) {
        int separator = listingKey.indexOf('|');
        return separator < 0 ? listingKey : listingKey.substring(0, separator);
    }

    /** Declared keys accepted by the last commit (input for the unrestored calculation). */
    private final Set<String> acceptedDeclaredKeys = new LinkedHashSet<>();

    /** Root close: drops the domain records so independent test roots start clean. */
    public synchronized void reset() {
        committedGeneration = 0L;
        committedSnapshot = VillagerTradeSetSnapshot.EMPTY;
        acceptedDeclaredKeys.clear();
        unrestoredKeys.clear();
        retiredKeys.clear();
    }

    /** Diagnostic copy of the current records (no live collections leave this object). */
    public synchronized Map<String, Object> describeRecords() {
        Map<String, Object> records = new LinkedHashMap<>();
        records.put("generation", committedGeneration);
        records.put("adapter", adapterId);
        records.put("available", available);
        records.put("tradeSets", committedSnapshot.tradeSetIds());
        records.put("unrestored", List.copyOf(unrestoredKeys));
        records.put("retired", List.copyOf(retiredKeys));
        return records;
    }
}
