package com.tkisor.nekojs.api.surface;

import java.util.Objects;

public record LoaderVersionRange(LoaderVersion min, LoaderVersion max, boolean exact) {

    public static LoaderVersionRange exact(LoaderVersion version) {
        Objects.requireNonNull(version, "version");
        return new LoaderVersionRange(version, version, true);
    }

    public static LoaderVersionRange range(LoaderVersion min, LoaderVersion max) {
        Objects.requireNonNull(min, "min");
        Objects.requireNonNull(max, "max");
        return new LoaderVersionRange(min, max, false);
    }

    public boolean matches(LoaderVersion version) {
        Objects.requireNonNull(version, "version");
        if (exact) {
            return min.compareTo(version) == 0;
        }
        return min.compareTo(version) <= 0 && version.compareTo(max) < 0;
    }
}
