package com.tkisor.nekojs.api.surface;

import java.util.Objects;

public record ApiEnvironmentSnapshot(
        EnvironmentKey environmentKey,
        ApiSurfaceSnapshot surfaceSnapshot,
        ApiContractHashes contractHashes
) {
    public ApiEnvironmentSnapshot {
        Objects.requireNonNull(environmentKey, "environmentKey");
        Objects.requireNonNull(surfaceSnapshot, "surfaceSnapshot");
        Objects.requireNonNull(contractHashes, "contractHashes");
    }
}
