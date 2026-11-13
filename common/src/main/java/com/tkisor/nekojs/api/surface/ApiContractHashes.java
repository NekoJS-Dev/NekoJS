package com.tkisor.nekojs.api.surface;

import java.util.Map;
import java.util.Objects;

/**
 * Snapshot of the API contract hashes: the portable API version and contract hash, plus one
 * contract hash per module.
 *
 * @param portableApiVersion   portable API version string
 * @param portableContractHash portable contract hash
 * @param moduleContractHashes module id to contract hash (copied into an immutable map)
 */
public record ApiContractHashes(
        String portableApiVersion,
        String portableContractHash,
        Map<String, String> moduleContractHashes
) {
    public ApiContractHashes {
        moduleContractHashes = Map.copyOf(moduleContractHashes == null ? Map.of() : moduleContractHashes);
    }
}
