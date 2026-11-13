package com.tkisor.nekojs.api.surface;

import java.util.Objects;

/**
 * Snapshot of the API runtime versions: the NekoJS version, the API, SPI and runtime contract
 * versions, and the catalog schema version.
 *
 * @param nekojsVersion          NekoJS version string, must not be {@code null}
 * @param apiVersion             API version, must not be {@code null}
 * @param spiVersion             SPI version, must not be {@code null}
 * @param runtimeContractVersion runtime contract version, must not be {@code null}
 * @param catalogSchemaVersion   catalog schema version (must be positive)
 */
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
