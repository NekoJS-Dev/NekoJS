package com.tkisor.nekojs.api.villager;

/**
 * Collects trades for the candidate SERVER generation without mutating the live registry.
 * Invalid configuration rejects the batch; publication remains owned by the runtime reload.
 */
public interface VillagerTradeDeclarationEvent {
    /** Adds a trade configuration to an existing trade set. */
    void add(String tradeSet, Object config);

    /** Number of declarations recorded during this callback payload. */
    int getAddedCount();
}
