package com.tkisor.nekojs.api.contract;

import com.tkisor.nekojs.api.surface.ApiVersion;

import java.util.Objects;

/**
 * Stable identity of an API contract: owner + kind + id + version.
 *
 * <p>Used to index and look up contracts. None of the four fields may be {@code null}.
 *
 * @param owner      contract owner (the plugin or platform identifier)
 * @param kind       contract kind
 * @param contractId contract id
 * @param version    contract version
 */
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
