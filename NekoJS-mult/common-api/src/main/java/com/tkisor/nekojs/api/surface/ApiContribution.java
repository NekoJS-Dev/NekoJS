package com.tkisor.nekojs.api.surface;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record ApiContribution(
        ApiSymbolId symbolId,
        ApiTier tier,
        String jsName,
        Set<ScriptTypeId> scriptTypes,
        List<ApiSignature> signatures,
        boolean nativeReturn,
        ApiCallHandler handler
) {
    public ApiContribution {
        Objects.requireNonNull(symbolId, "symbolId");
        Objects.requireNonNull(tier, "tier");
        Objects.requireNonNull(jsName, "jsName");
        scriptTypes = Set.copyOf(scriptTypes == null ? Set.of() : scriptTypes);
        signatures = List.copyOf(signatures == null ? List.of() : signatures);
        Objects.requireNonNull(handler, "handler");
        if (signatures.isEmpty()) {
            throw new IllegalArgumentException("contribution must have at least one signature");
        }
    }

    public static ApiContribution symbol(
            ApiSymbolId symbolId,
            ApiTier tier,
            String jsName,
            Set<ScriptTypeId> scriptTypes,
            List<ApiSignature> signatures,
            ApiCallHandler handler) {
        return new ApiContribution(symbolId, tier, jsName, scriptTypes, signatures, false, handler);
    }

    public static ApiContribution withNativeReturn(
            ApiSymbolId symbolId,
            ApiTier tier,
            String jsName,
            Set<ScriptTypeId> scriptTypes,
            List<ApiSignature> signatures,
            ApiCallHandler handler) {
        return new ApiContribution(symbolId, tier, jsName, scriptTypes, signatures, true, handler);
    }
}
