package com.tkisor.nekojs.api.surface;

import java.util.*;

public record ApiSymbol(ApiSymbolId id, List<ApiSignature> signatures) {

    public ApiSymbol {
        Objects.requireNonNull(id, "id");
        if (signatures == null || signatures.isEmpty()) {
            throw new IllegalArgumentException("symbol must have at least one signature");
        }
        signatures = List.copyOf(signatures);
        // Reject duplicate callKey - same params different return type is illegal JS overload
        Set<String> seen = new HashSet<>();
        for (ApiSignature sig : signatures) {
            if (!seen.add(sig.callKey())) {
                throw new IllegalArgumentException(
                        "duplicate callKey in symbol " + id + ": " + sig.callKey());
            }
        }
    }
}
