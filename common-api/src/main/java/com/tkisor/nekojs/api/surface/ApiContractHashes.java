package com.tkisor.nekojs.api.surface;

import java.util.Map;
import java.util.Objects;

public record ApiContractHashes(
        String portableApiVersion,
        String portableContractHash,
        Map<String, String> moduleContractHashes
) {
    public ApiContractHashes {
        moduleContractHashes = Map.copyOf(moduleContractHashes == null ? Map.of() : moduleContractHashes);
    }
}
