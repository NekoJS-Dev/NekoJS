package com.tkisor.nekojs.api.surface;

import java.util.Objects;

public record ApiRuntimeVersions(
        String nekojsVersion,
        ApiVersion apiVersion,
        ApiVersion spiVersion,
        ApiVersion runtimeContractVersion,
        int catalogSchemaVersion
) {
    public ApiRuntimeVersions {
        Objects.requireNonNull(nekojsVersion, "nekojsVersion");
        Objects.requireNonNull(apiVersion, "apiVersion");
        Objects.requireNonNull(spiVersion, "spiVersion");
        Objects.requireNonNull(runtimeContractVersion, "runtimeContractVersion");
        if (catalogSchemaVersion < 1) {
            throw new IllegalArgumentException("catalogSchemaVersion must be positive");
        }
    }
}
