package com.tkisor.nekojs.villager;

import com.tkisor.nekojs.core.contribution.CommittedContributionLifecycle;

/**
 * Platform seam for applying a complete {@link VillagerTradePlan}.
 *
 * <p>Implementations live in the version tree. On NeoForge 26.x the native
 * integration point is after the server's reloadable registries have published
 * a new epoch (currently the server-data/tags-loaded barrier); the adapter then
 * owns baseline recapture, injected-key cleanup, frozen registry handling, and
 * native mutation. A Fabric adapter must perform the equivalent native work in
 * its own version node. No script-facing payload should call this interface.
 */
public interface VillagerTradeRegistryAdapter<EPOCH>
        extends CommittedContributionLifecycle.Adapter<VillagerTradePlan, EPOCH> {
}
