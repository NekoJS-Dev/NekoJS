package com.tkisor.nekojs.api.surface;

import java.util.List;
import java.util.Objects;

public record ApiCallContext(
        EnvironmentKey environment,
        ApiSymbolId symbolId,
        ApiSignature signature
) {
    public ApiCallContext {
        Objects.requireNonNull(environment, "environment");
    }
}
