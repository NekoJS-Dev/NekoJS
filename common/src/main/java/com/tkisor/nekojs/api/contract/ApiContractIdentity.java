package com.tkisor.nekojs.api.contract;

import com.tkisor.nekojs.api.surface.ApiVersion;

import java.util.Objects;

public record ApiContractIdentity(
        String owner,
        ApiContractKind kind,
        String contractId,
        ApiVersion version
) {
    public ApiContractIdentity {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(contractId, "contractId");
        Objects.requireNonNull(version, "version");
    }
}
