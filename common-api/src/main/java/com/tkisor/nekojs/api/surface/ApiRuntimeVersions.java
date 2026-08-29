package com.tkisor.nekojs.api.surface;

import java.util.Objects;

/**
 * API 运行时版本快照：NekoJS 版本、API/SPI/运行时契约版本与 catalog schema 版本。
 *
 * @param nekojsVersion          NekoJS 版本字符串，不能为 {@code null}
 * @param apiVersion             API 版本，不能为 {@code null}
 * @param spiVersion             SPI 版本，不能为 {@code null}
 * @param runtimeContractVersion 运行时契约版本，不能为 {@code null}
 * @param catalogSchemaVersion   catalog schema 版本（须为正数）
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
