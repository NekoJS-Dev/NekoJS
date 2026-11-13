package com.tkisor.nekojs.api.surface;

import java.util.*;

/**
 * One API symbol: identified by an {@link ApiSymbolId} and carrying one or more overload
 * signatures.
 *
 * <p>The constructor requires at least one signature and rejects a duplicate {@code callKey}
 * (parameter shape only), so two JS overloads cannot differ by return type alone.
 */
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
