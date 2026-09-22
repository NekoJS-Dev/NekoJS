package com.tkisor.nekojs.core.dynamic.txn;

import com.tkisor.nekojs.core.dynamic.plan.DynamicAdapterRequest;

import java.util.List;

/**
 * Execution boundary between the Dynamic Registry transaction coordinator and the
 * platform/version Adapter (ticket 21, AC5): numeric ids, registry surgery, claim
 * bookkeeping, cleanup and platform differences are executed <b>only</b> by Adapter
 * implementations of this interface. The common coordinator and the event facade
 * hold no Minecraft types and no reflection channel — they only hand over inert
 * {@link DynamicAdapterRequest} descriptions.
 *
 * <p>Contract:
 * <ul>
 *   <li>{@link #prepareActivation(List)} validates a batch (configuration gate,
 *       supported types, server/registry availability) and may throw to reject the
 *       <b>whole batch</b> — the rejection aborts the transaction before any client
 *       traffic is sent;</li>
 *   <li>{@link #activate(List)} performs the surgery. It is only called after a
 *       successful {@code prepareActivation} of the same requests and must not throw;
 *       if an implementation violates that, the coordinator records a degraded
 *       activation failure (the ledger keeps serving the previously activated state)
 *       instead of pretending success;</li>
 *   <li>implementations must be idempotent per {@code (key, fingerprint)}: the same
 *       request arriving again (retry batch, client catch-up) re-claims the entry
 *       instead of double-registering.</li>
 * </ul>
 */
public interface DynamicRegistryAdapter {

    /**
     * Validates a batch of inert register requests. Throws to reject the whole batch
     * (the throwable's message becomes the abort detail).
     */
    void prepareActivation(List<DynamicAdapterRequest> requests);

    /**
     * Executes a previously validated batch. Must not throw after
     * {@link #prepareActivation(List)} accepted the same requests.
     */
    void activate(List<DynamicAdapterRequest> requests);
}
