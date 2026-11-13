package com.tkisor.nekojs.api.surface;

import java.util.Objects;

/**
 * A loader version range: either an exact match or the half-open interval {@code [min, max)}.
 *
 * @param min   lower bound
 * @param max   upper bound
 * @param exact whether this is an exact match (min equals max when true)
 */
public record LoaderVersionRange(LoaderVersion min, LoaderVersion max, boolean exact) {

    /** Matches exactly the given version. */
    public static LoaderVersionRange exact(LoaderVersion version) {
        Objects.requireNonNull(version, "version");
        return new LoaderVersionRange(version, version, true);
    }

    /** Builds the half-open interval {@code [min, max)}. */
    public static LoaderVersionRange range(LoaderVersion min, LoaderVersion max) {
        Objects.requireNonNull(min, "min");
        Objects.requireNonNull(max, "max");
        return new LoaderVersionRange(min, max, false);
    }

    /** Returns whether the version falls in this range: equality when exact, otherwise
     * {@code min <= v < max}. */
    public boolean matches(LoaderVersion version) {
        Objects.requireNonNull(version, "version");
        if (exact) {
            return min.compareTo(version) == 0;
        }
        return min.compareTo(version) <= 0 && version.compareTo(max) < 0;
    }
}
