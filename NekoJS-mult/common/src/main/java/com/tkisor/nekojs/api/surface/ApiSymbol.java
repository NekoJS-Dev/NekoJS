package com.tkisor.nekojs.api.surface;

import java.util.*;

/**
 * 一个 API 符号：由 {@link ApiSymbolId} 标识，携带一个或多个重载签名。
 *
 * <p>构造时校验：至少一个签名，且同一 {@code callKey}（仅参数形状）不能重复——
 * 即不允许仅返回类型不同的 JS 重载。
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
