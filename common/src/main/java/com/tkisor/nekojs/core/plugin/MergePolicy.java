package com.tkisor.nekojs.core.plugin;

import org.slf4j.Logger;

/**
 * Merge policy for extension points, with four tiers: {@code append} (list-style collection
 * with no conflicts), {@code firstWin} (the first contribution for a key wins, plus a warning),
 * {@code overrideWarn} (the new value replaces the old one, plus a warning) and
 * {@code failFast} (a duplicate key throws immediately).
 *
 * <p>Each extension point declares its policy explicitly through the builder call
 * {@code merge(...)}; there is no default. A keyed accumulator calls {@link #resolveDuplicate}
 * when a second contribution arrives for the same key, and the policy decides whether the old
 * or the new value wins. A list-style (append) accumulator never calls it.
 */
public final class MergePolicy {

    /** Identifies one of the four tiers. */
    public enum Tier {
        APPEND, FIRST_WIN, OVERRIDE_WARN, FAIL_FAST
    }

    private final Tier tier;

    private MergePolicy(Tier tier) {
        this.tier = tier;
    }

    /** List-style collection: every contribution is kept, so no key ever conflicts. */
    public static MergePolicy append() {
        return new MergePolicy(Tier.APPEND);
    }

    /** The first contribution for a key wins; later ones are ignored with a warning (for example
     * node_modules). */
    public static MergePolicy firstWin() {
        return new MergePolicy(Tier.FIRST_WIN);
    }

    /** The new value replaces the old one for the same key, with a warning: the later declaration
     * wins. */
    public static MergePolicy overrideWarn() {
        return new MergePolicy(Tier.OVERRIDE_WARN);
    }

    /** A duplicate key throws immediately (for example recipe_namespaces). */
    public static MergePolicy failFast() {
        return new MergePolicy(Tier.FAIL_FAST);
    }

    public Tier tier() {
        return tier;
    }

    /**
     * Decides a conflict in a keyed accumulator: called when a second contribution arrives for
     * the same key.
     *
     * @param pointId     extension point id, for diagnostics
     * @param contributor who contributed, usually the plugin class name, for diagnostics
     * @param key         the conflicting key
     * @param logger      warning output
     * @return {@code true} keeps the new value (override), {@code false} keeps the old one (first win)
     * @throws IllegalArgumentException thrown in the {@code failFast} tier
     * @throws IllegalStateException    calling this method on the {@code append} tier is a usage error
     */
    public boolean resolveDuplicate(Object pointId, String contributor, String key, Logger logger) {
        return switch (tier) {
            case APPEND -> throw new IllegalStateException(
                    "Plugin extension point '" + pointId + "' declares append; keyed conflict on '"
                            + key + "' should not happen");
            case FIRST_WIN -> {
                logger.warn("Plugin extension point '{}' key '{}' already registered by a higher-priority "
                        + "plugin; ignoring duplicate (firstWin)", pointId, key);
                yield false;
            }
            case OVERRIDE_WARN -> {
                logger.warn("Plugin extension point '{}' key '{}' overridden by {} (overrideWarn)",
                        pointId, key, contributor);
                yield true;
            }
            case FAIL_FAST -> throw new IllegalArgumentException(
                    "Plugin extension point '" + pointId + "' conflict on key '" + key
                            + "' (failFast). Possible plugin conflict.");
        };
    }

    @Override
    public String toString() {
        return switch (tier) {
            case APPEND -> "append";
            case FIRST_WIN -> "firstWin";
            case OVERRIDE_WARN -> "overrideWarn";
            case FAIL_FAST -> "failFast";
        };
    }
}
