package com.tkisor.nekojs.authoring;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Root-lifetime client owner dispatch, independent of any connected server. */
public final class AuthoringClientExecutor implements Executor {
    private static final AtomicLong NEXT_EPOCH = new AtomicLong(1);

    private final Executor executor;
    private final BooleanSupplier ownerThread;
    private final BooleanSupplier active;
    private final long rootEpoch = nextEpoch();

    // Shared with the server bridge so target lifetimes cannot accidentally share an epoch.
    static long nextEpoch() {
        return NEXT_EPOCH.getAndIncrement();
    }

    public AuthoringClientExecutor(Executor executor, BooleanSupplier ownerThread, BooleanSupplier active) {
        this.executor = Objects.requireNonNull(executor);
        this.ownerThread = Objects.requireNonNull(ownerThread);
        this.active = Objects.requireNonNull(active);
    }

    public long epoch() {
        return active.getAsBoolean() ? rootEpoch : -1;
    }

    @Override
    public void execute(Runnable operation) {
        Objects.requireNonNull(operation);
        if (epoch() < 0) throw staleRuntime();
        // Queued callbacks must still reach the core's epoch check after shutdown; never silently drop them.
        if (ownerThread.getAsBoolean()) operation.run();
        else executor.execute(operation);
    }

    /** Runs a synchronous candidate only on its active owner, checking lifetime again after completion. */
    public <T> T call(Supplier<T> operation) {
        Objects.requireNonNull(operation);
        requireOwner();
        T result = operation.get();
        requireOwner();
        return result;
    }

    private void requireOwner() {
        if (epoch() < 0) throw staleRuntime();
        if (!ownerThread.getAsBoolean()) {
            throw new IllegalStateException("[NEKO-1023] Live controls require the client owner thread.");
        }
    }

    private static IllegalStateException staleRuntime() {
        return new IllegalStateException("[NEKO-1024] Live client runtime is no longer current.");
    }
}
