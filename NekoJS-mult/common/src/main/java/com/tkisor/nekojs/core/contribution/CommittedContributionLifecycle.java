package com.tkisor.nekojs.core.contribution;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Deep, platform-free implementation of the committed-contribution protocol.
 *
 * <p>The implementation owns the state transitions and deliberately knows
 * nothing about Minecraft or a native registry. The adapter is called only
 * after both a plan and an epoch are available. Reconciliation is recorded
 * only after the adapter returns normally, so an application failure can be
 * retried for the same epoch. Plan revisions are tracked separately from
 * epochs: a new successful script reload is applied even when the registry
 * object did not change, while repeated notifications for one revision/epoch
 * pair are idempotent.
 */
public final class CommittedContributionLifecycle<COLLECTOR, PLAN, EPOCH>
        implements ContributionLifecycle<COLLECTOR, PLAN, EPOCH> {

    /** Native seam used by a committed contribution lifecycle. */
    @FunctionalInterface
    public interface Adapter<PLAN, EPOCH> {
        /** Reconciles the complete plan against the supplied native epoch. */
        void reconcile(PLAN plan, EPOCH epoch);

        /** Clears adapter-owned snapshots and injected state on host shutdown. */
        default void reset() {
        }
    }

    private final Supplier<? extends COLLECTOR> collectorFactory;
    private final Function<? super COLLECTOR, ? extends PLAN> sealer;
    private final Adapter<? super PLAN, ? super EPOCH> adapter;

    private COLLECTOR candidate;
    private PLAN committed;
    private EPOCH registryEpoch;
    private EPOCH appliedEpoch;
    private long committedRevision;
    private long appliedRevision = -1L;
    private boolean collecting;
    private boolean sealed;

    public CommittedContributionLifecycle(
            Supplier<? extends COLLECTOR> collectorFactory,
            Function<? super COLLECTOR, ? extends PLAN> sealer,
            Adapter<? super PLAN, ? super EPOCH> adapter) {
        this.collectorFactory = Objects.requireNonNull(collectorFactory, "collectorFactory");
        this.sealer = Objects.requireNonNull(sealer, "sealer");
        this.adapter = Objects.requireNonNull(adapter, "adapter");
    }

    /**
     * Begins a fresh candidate. Existing committed state remains available until
     * this candidate is sealed successfully.
     */
    @Override
    public synchronized void begin() {
        COLLECTOR next = Objects.requireNonNull(collectorFactory.get(), "collectorFactory returned null");
        candidate = next;
        collecting = true;
        sealed = false;
    }

    /** Returns the current candidate collector. */
    @Override
    public synchronized COLLECTOR collect() {
        if (!collecting) {
            throw new IllegalStateException("No contribution candidate is collecting; call begin() first");
        }
        return candidate;
    }

    /**
     * Seals the candidate once. Calling this after a successful seal is
     * idempotent and returns the same committed plan.
     */
    @Override
    public synchronized PLAN seal() {
        if (sealed) {
            return committed;
        }
        if (!collecting) {
            throw new IllegalStateException("No contribution candidate is collecting; call begin() first");
        }

        PLAN next = Objects.requireNonNull(sealer.apply(candidate), "sealer returned null");
        committed = next;
        committedRevision++;
        candidate = null;
        collecting = false;
        sealed = true;
        reconcileIfReady();
        return committed;
    }

    /**
     * Publishes the current native epoch. The full plan is reconciled at most
     * once for each plan revision and epoch pair.
     */
    @Override
    public synchronized boolean registryReady(EPOCH epoch) {
        registryEpoch = Objects.requireNonNull(epoch, "epoch");
        return reconcileIfReady();
    }

    /** Clears all lifecycle state and delegates native cleanup to the adapter. */
    @Override
    public synchronized void stop() {
        candidate = null;
        committed = null;
        registryEpoch = null;
        appliedEpoch = null;
        committedRevision = 0L;
        appliedRevision = -1L;
        collecting = false;
        sealed = false;
        adapter.reset();
    }

    @Override
    public synchronized PLAN committedPlan() {
        return committed;
    }

    /** True after {@link #begin()} and before a successful {@link #seal()}. */
    public synchronized boolean isCollecting() {
        return collecting;
    }

    /** True after a candidate has been sealed and before {@link #stop()}. */
    public synchronized boolean hasSealedPlan() {
        return sealed;
    }

    /** The most recently published native epoch, or {@code null}. */
    public synchronized EPOCH registryEpoch() {
        return registryEpoch;
    }

    /** Number of successfully sealed candidate plans since the last stop. */
    public synchronized long committedRevision() {
        return committedRevision;
    }

    private boolean reconcileIfReady() {
        if (!sealed || registryEpoch == null) {
            return false;
        }
        if (appliedRevision == committedRevision && Objects.equals(appliedEpoch, registryEpoch)) {
            return false;
        }

        adapter.reconcile(committed, registryEpoch);
        appliedEpoch = registryEpoch;
        appliedRevision = committedRevision;
        return true;
    }
}
