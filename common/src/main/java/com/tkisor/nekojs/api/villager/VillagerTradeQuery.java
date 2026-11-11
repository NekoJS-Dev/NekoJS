package com.tkisor.nekojs.api.villager;

import java.util.List;

/**
 * Read-only snapshot returned by a generation-bound villager trade query.
 * A query from an inactive generation returns explicit stale status and empty counts/lists;
 * it never exposes a different generation's registry state.
 */
public interface VillagerTradeQuery {
    /**
     * The original enum object, named ACTIVE or STALE. This public view promises Java Enum
     * members without replacing the native enum or exposing its implementation class.
     */
    Enum<?> getStatus();

    /** Reason for stale status, or active-generation for an active query. */
    String getStatusReason();

    /** Committed generation number, or the stale token's generation when available. */
    long getGeneration();

    /** Adapter that committed this snapshot, or null for stale queries. */
    String getAdapterId();

    /** Current generation's declarations, excluding retained omissions; zero when stale. */
    int getTotal();

    /** Trade sets declared by the current generation; empty when stale. */
    List<String> getTradeSetIds();

    /** Current generation's declarations in the set; zero when stale or untouched. */
    int countOf(String tradeSetId);

    /** Retained listings omitted by the latest generation; empty when stale. */
    List<String> getUnrestoredListingKeys();

    /** Listings retired by an explicit obsolete-set declaration; empty when stale. */
    List<String> getRetiredListingKeys();

    /** Deterministic developer-facing summary of the snapshot. */
    String describe();
}
