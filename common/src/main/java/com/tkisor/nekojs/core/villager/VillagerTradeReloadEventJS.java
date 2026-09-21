package com.tkisor.nekojs.core.villager;

import java.util.List;
import java.util.Set;

/**
 * Payload of the {@code ServerEvents.tradeReload} reload sub-event (ticket 22 AC7).
 *
 * <p>Ordinary reload never physically deletes trades other scripts still rely on: a trade set
 * keeps serving its previous content, and the entries of the reloading script enter the
 * stale/retired record (visible through the query surface). A script that owns a trade set and
 * is done with it declares that explicitly here; the declaration takes effect in the same
 * commit, so removal is a transaction, not a side effect in the middle of collection.
 */
public final class VillagerTradeReloadEventJS {

    private final VillagerTradeCandidatePlan plan;
    private final VillagerTradeSetSnapshot incoming;

    public VillagerTradeReloadEventJS(VillagerTradeCandidatePlan plan, VillagerTradeSetSnapshot incoming) {
        this.plan = plan;
        this.incoming = incoming;
    }

    /** Trade sets that will carry declarations once this batch commits (read-only view). */
    public List<String> getTradeSets() {
        return incoming.tradeSetIds();
    }

    /** Declarations this batch would put into {@code tradeSetId} (0 when the set is untouched). */
    public int countOf(String tradeSetId) {
        return incoming.countOf(tradeSetId);
    }

    /** Total declarations in this batch. */
    public int getTotal() {
        return incoming.total();
    }

    /**
     * Marks a trade set as no longer managed: at the commit point the adapter restores that
     * set to its vanilla baseline and drops the entries NekoJS injected into it.
     */
    public void declareObsolete(String tradeSetId) {
        plan.markObsoleteTradeSet(tradeSetId);
    }
}
