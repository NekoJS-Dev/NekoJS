package com.tkisor.nekojs.villager;

import com.tkisor.nekojs.core.contribution.CommittedContributionLifecycle;
import com.tkisor.nekojs.core.contribution.ContributionLifecycle;

/**
 * Trade-specific facade over the shared committed-contribution lifecycle.
 *
 * <p>The facade keeps the caller's interface small: begin a candidate, collect
 * declarative offers, seal a full plan, publish native registry epochs, and
 * reset on server stop. It never exposes a server, registry, or native trade
 * object.
 */
public final class VillagerTradeLifecycle<EPOCH>
        implements ContributionLifecycle<VillagerTradePlan.Builder, VillagerTradePlan, EPOCH> {
    private final CommittedContributionLifecycle<VillagerTradePlan.Builder, VillagerTradePlan, EPOCH> delegate;

    public VillagerTradeLifecycle(VillagerTradeRegistryAdapter<EPOCH> adapter) {
        delegate = new CommittedContributionLifecycle<>(
                VillagerTradePlan::builder,
                VillagerTradePlan.Builder::seal,
                adapter);
    }

    @Override
    public void begin() {
        delegate.begin();
    }

    @Override
    public VillagerTradePlan.Builder collect() {
        return delegate.collect();
    }

    @Override
    public VillagerTradePlan seal() {
        return delegate.seal();
    }

    @Override
    public boolean registryReady(EPOCH epoch) {
        return delegate.registryReady(epoch);
    }

    @Override
    public void stop() {
        delegate.stop();
    }

    @Override
    public VillagerTradePlan committedPlan() {
        return delegate.committedPlan();
    }

    public boolean isCollecting() {
        return delegate.isCollecting();
    }

    public boolean hasSealedPlan() {
        return delegate.hasSealedPlan();
    }

    public EPOCH registryEpoch() {
        return delegate.registryEpoch();
    }

    public long committedRevision() {
        return delegate.committedRevision();
    }
}
