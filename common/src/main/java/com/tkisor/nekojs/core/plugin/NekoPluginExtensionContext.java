package com.tkisor.nekojs.core.plugin;

/**
 * Bootstrap context available while extension points collect: environment information plus the
 * finished products of earlier points.
 *
 * <p><b>Two levels of access:</b>
 * <ul>
 *   <li>{@link #result} for an optional dependency: returns {@code null} when the other point is
 *       unregistered or skipped by the environment, and the caller decides what to do;</li>
 *   <li>{@link #resultOrThrow} for a required dependency: throws {@link IllegalStateException}
 *       whenever the other point is missing (unregistered, skipped by the environment, or not
 *       finished yet).</li>
 * </ul>
 *
 * <p><b>Out-of-order data reads:</b> the topological order finishes every point named in
 * {@code dependsOn} first. Reading a point that is registered but not finished (a missing
 * {@code dependsOn} declaration, or a broken graph) also makes {@link #result} throw
 * {@link IllegalStateException}, with a "declare dependsOn" hint in the message, so a missing
 * declaration never fails silently.
 */
public interface NekoPluginExtensionContext {

    /** Whether the current process is a client; a dedicated server returns {@code false}. */
    boolean client();

    /**
     * Looks up the product of an earlier point by extension point instance (optional
     * dependency).
     *
     * @return the product; {@code null} when the point is unregistered or skipped by the
     *         environment; throws {@link IllegalStateException} (with a fix hint) when the point
     *         is registered but has not finished yet
     */
    <R> R result(NekoPluginExtensionPoint<?, ?, R> point);

    /**
     * Looks up the product of an earlier point by extension point id (optional dependency).
     *
     * @return the product; {@code null} when the point is unregistered, skipped by the
     *         environment, or its finisher returned null; throws {@link IllegalStateException}
     *         when the product type does not match or the point is registered but has not
     *         finished yet
     */
    <R> R result(String pointId, Class<R> type);

    /**
     * Looks up the product of an earlier point by extension point instance (required
     * dependency).
     *
     * @return the product
     * @throws IllegalStateException the point is unregistered, skipped by the environment, or
     *                               not finished yet
     */
    <R> R resultOrThrow(NekoPluginExtensionPoint<?, ?, R> point);

    /**
     * Looks up the product of an earlier point by extension point id (required dependency).
     *
     * @return the product
     * @throws IllegalStateException the point is unregistered, skipped by the environment, not
     *                               finished yet, or the product type does not match
     */
    <R> R resultOrThrow(String pointId, Class<R> type);
}
