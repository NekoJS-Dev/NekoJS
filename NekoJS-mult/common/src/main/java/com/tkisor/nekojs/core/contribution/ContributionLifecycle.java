package com.tkisor.nekojs.core.contribution;

/**
 * The public seam for a reload-owned contribution product.
 *
 * <p>A caller starts a candidate, obtains its collector, seals a successful
 * candidate, publishes a platform epoch, and resets the module when the host
 * stops. A candidate that is never sealed cannot replace the committed plan.
 */
public interface ContributionLifecycle<COLLECTOR, PLAN, EPOCH> {
    /** Starts a new candidate collection without changing the committed plan. */
    void begin();

    /** Returns the mutable collector for the candidate started by {@link #begin()}. */
    COLLECTOR collect();

    /** Seals the current candidate as an immutable plan and returns it. */
    PLAN seal();

    /** Publishes a native registry epoch and reconciles when a sealed plan exists. */
    boolean registryReady(EPOCH epoch);

    /** Clears candidate, committed, epoch, and adapter state for host shutdown. */
    void stop();

    /** Returns the last successfully sealed plan, or {@code null} before the first seal. */
    PLAN committedPlan();
}
