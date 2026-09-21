package com.tkisor.nekojs.core.villager;

import java.util.List;

/**
 * Read-only, generation-bound query result of the {@code VillagerTrades.query()} binding
 * (ticket 22 AC3/AC7).
 *
 * <p>It is a snapshot of what the last commit published plus the domain bookkeeping records —
 * never a live registry view, never a mutable manager, never a writable object. A token read
 * from a generation that is no longer the active one reports {@link Status#STALE} and gives
 * deterministic empty/zero answers for every member instead of stale data.
 */
public final class VillagerTradeQuerySurface {

    public enum Status {
        /** The reading generation is the active SERVER generation: values are the last commit's. */
        ACTIVE,
        /** The reading generation is not active any more (superseded, candidate, closed, unregistered). */
        STALE
    }

    private final Status status;
    private final String reason;
    private final long generation;
    private final String adapterId;
    private final int total;
    private final List<String> tradeSetIds;
    private final java.util.Map<String, Integer> counts;
    private final List<String> unrestoredKeys;
    private final List<String> retiredKeys;

    private VillagerTradeQuerySurface(Status status, String reason, long generation, String adapterId,
            int total, List<String> tradeSetIds, java.util.Map<String, Integer> counts,
            List<String> unrestoredKeys, List<String> retiredKeys) {
        this.status = status;
        this.reason = reason;
        this.generation = generation;
        this.adapterId = adapterId;
        this.total = total;
        this.tradeSetIds = List.copyOf(tradeSetIds);
        this.counts = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(counts));
        this.unrestoredKeys = List.copyOf(unrestoredKeys);
        this.retiredKeys = List.copyOf(retiredKeys);
    }

    static VillagerTradeQuerySurface stale(String reason, long generation) {
        return new VillagerTradeQuerySurface(Status.STALE, reason, generation, null, 0,
                List.of(), java.util.Map.of(), List.of(), List.of());
    }

    static VillagerTradeQuerySurface active(long generation, String adapterId, VillagerTradeSetSnapshot snapshot,
            List<String> unrestoredKeys, List<String> retiredKeys) {
        return new VillagerTradeQuerySurface(Status.ACTIVE, "active-generation", generation, adapterId,
                snapshot.total(), snapshot.tradeSetIds(), snapshot.countsByTradeSet(), unrestoredKeys, retiredKeys);
    }

    /** Explicit "no villager trade adapter on this node" result (never a silent empty snapshot). */
    public static VillagerTradeQuerySurface unavailable(String reason) {
        return new VillagerTradeQuerySurface(Status.STALE, "unavailable:" + reason, -1L, null, 0,
                List.of(), java.util.Map.of(), List.of(), List.of());
    }

    /** {@code ACTIVE} when the calling generation still is the active one, {@code STALE} otherwise. */
    public Status getStatus() {
        return status;
    }

    /** Why the token is stale ({@code active-generation} when it is not). */
    public String getStatusReason() {
        return reason;
    }

    /** Committed generation number this snapshot belongs to ({@code -1} for stale tokens). */
    public long getGeneration() {
        return generation;
    }

    /** Adapter that committed the snapshot ({@code null} for stale tokens). */
    public String getAdapterId() {
        return adapterId;
    }

    /** Total declarations NekoJS injected (0 for stale tokens). */
    public int getTotal() {
        return total;
    }

    /** Trade sets carrying NekoJS declarations (empty for stale tokens). */
    public List<String> getTradeSetIds() {
        return tradeSetIds;
    }

    /** Declarations NekoJS injected into {@code tradeSetId} (0 for stale tokens). */
    public int countOf(String tradeSetId) {
        return counts.getOrDefault(tradeSetId, 0);
    }

    /**
     * Listings that are still active in the registry but that the latest generation no longer
     * declares: they are recorded here instead of being physically deleted by an ordinary
     * reload (ticket 22 AC7).
     */
    public List<String> getUnrestoredListingKeys() {
        return unrestoredKeys;
    }

    /** Listings retired because their script declared the trade set obsolete (ticket 22 AC7). */
    public List<String> getRetiredListingKeys() {
        return retiredKeys;
    }

    /** Deterministic developer-facing summary (diagnostics, commands, tests). */
    public String describe() {
        if (status == Status.STALE) {
            return "villager-trades query: STALE (" + reason + "); no snapshot is exposed to this generation";
        }
        StringBuilder builder = new StringBuilder("villager-trades query: ACTIVE generation=").append(generation)
                .append(" adapter=").append(adapterId).append(" trades=").append(total);
        for (String tradeSetId : tradeSetIds) {
            builder.append(' ').append(tradeSetId).append('=').append(counts.getOrDefault(tradeSetId, 0));
        }
        if (!unrestoredKeys.isEmpty()) {
            builder.append(" unrestored=").append(unrestoredKeys);
        }
        if (!retiredKeys.isEmpty()) {
            builder.append(" retired=").append(retiredKeys);
        }
        return builder.toString();
    }
}
