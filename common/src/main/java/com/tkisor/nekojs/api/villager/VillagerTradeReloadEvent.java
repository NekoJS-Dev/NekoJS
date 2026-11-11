package com.tkisor.nekojs.api.villager;

import java.util.List;

/** Collects explicit trade-set retirement requests for the same candidate generation commit. */
public interface VillagerTradeReloadEvent {
    /** Trade sets declared by the incoming batch, not a live registry view. */
    List<String> getTradeSets();

    /** Number of declarations this batch would put into the trade set. */
    int countOf(String tradeSetId);

    /** Total declarations in the incoming batch. */
    int getTotal();

    /** Restores the set to its baseline at commit; ordinary omission does not retire listings. */
    void declareObsolete(String tradeSetId);
}
