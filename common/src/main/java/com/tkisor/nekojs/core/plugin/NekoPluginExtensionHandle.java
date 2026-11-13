package com.tkisor.nekojs.core.plugin;

/**
 * Handle returned by {@link NekoPluginExtensionRegistry#register}: a lightweight token that
 * fetches the finisher product once bootstrap has finished this extension point.
 *
 * <p>A handle belongs to one bootstrap run; every run registers the points again and returns
 * new handles. Calling {@link #result()} before the product is published throws
 * {@link IllegalStateException}. When an environment predicate skips the point, its handle
 * stays unpublished forever.
 */
public final class NekoPluginExtensionHandle<R> {

    private final String pointId;
    private volatile boolean finished;
    private volatile R product;

    NekoPluginExtensionHandle(String pointId) {
        this.pointId = pointId;
    }

    /** Extension point id this handle refers to. */
    public String pointId() {
        return pointId;
    }

    /** Whether this bootstrap run finished the point: its finisher has run and published the
     * product. */
    public boolean isFinished() {
        return finished;
    }

    /**
     * Returns the finisher product.
     *
     * @return the product; {@code null} when the finisher returned null
     * @throws IllegalStateException the point has not finished (bootstrap is incomplete or the
     *                               environment skipped the point)
     */
    public R result() {
        if (!finished) {
            throw new IllegalStateException(
                    "Plugin extension point '" + pointId + "' has not finished yet (bootstrap incomplete or point skipped)");
        }
        return product;
    }

    void publish(R product) {
        this.product = product;
        this.finished = true;
    }
}
