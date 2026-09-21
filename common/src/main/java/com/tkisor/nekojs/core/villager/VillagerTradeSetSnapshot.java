package com.tkisor.nekojs.core.villager;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Immutable read-only snapshot of the trade-set state a commit published (ticket 22 AC3):
 * container data only — {@code tradeSetId -> declaration count} plus the opaque registry epoch
 * the adapter observed while committing.
 *
 * <p>The snapshot is what generation-bound queries read. It never exposes a live registry
 * view, a mutable adapter collection or a NekoJS-owned manager object.
 */
public record VillagerTradeSetSnapshot(long registryEpoch, Map<String, Integer> countsByTradeSet) {

    /** No commit yet (queries report an explicit empty/absent state instead of guessing). */
    public static final VillagerTradeSetSnapshot EMPTY = new VillagerTradeSetSnapshot(-1L, Map.of());

    public VillagerTradeSetSnapshot {
        countsByTradeSet = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(countsByTradeSet));
    }

    public static VillagerTradeSetSnapshot of(long registryEpoch, Map<String, Integer> counts) {
        Map<String, Integer> sorted = new TreeMap<>(counts);
        return new VillagerTradeSetSnapshot(registryEpoch, sorted);
    }

    public boolean isEmpty() {
        return countsByTradeSet.isEmpty();
    }

    /** Trade set ids this commit injected at least one declaration into (sorted). */
    public List<String> tradeSetIds() {
        return List.copyOf(countsByTradeSet.keySet());
    }

    public int countOf(String tradeSetId) {
        return countsByTradeSet.getOrDefault(tradeSetId, 0);
    }

    public int total() {
        int total = 0;
        for (int count : countsByTradeSet.values()) {
            total += count;
        }
        return total;
    }
}
