package com.tkisor.nekojs.api.surface;

import java.util.Objects;

public record ApiVersionRange(ApiVersion min, ApiVersion max, boolean exact) {

    public static ApiVersionRange exact(ApiVersion version) {
        Objects.requireNonNull(version, "version");
        return new ApiVersionRange(version, version, true);
    }

    public static ApiVersionRange range(ApiVersion min, ApiVersion max) {
        Objects.requireNonNull(min, "min");
        Objects.requireNonNull(max, "max");
        return new ApiVersionRange(min, max, false);
    }

    public boolean matches(ApiVersion version) {
        Objects.requireNonNull(version, "version");
        if (exact) {
            return min.compareTo(version) == 0;
        }
        return min.compareTo(version) <= 0 && version.compareTo(max) < 0;
    }
}
