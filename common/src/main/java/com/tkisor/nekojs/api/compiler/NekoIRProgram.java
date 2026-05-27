package com.tkisor.nekojs.api.compiler;

import com.tkisor.nekojs.core.module.NekoModuleMode;

public record NekoIRProgram(
        String languageId,
        String code,
        String sourceMap,
        NekoModuleMode requestedMode,
        boolean module,
        boolean topLevelAwait
) {
    public NekoIRProgram {
        languageId = languageId == null || languageId.isBlank() ? "unknown" : languageId;
        code = code == null ? "" : code;
        requestedMode = requestedMode == null ? NekoModuleMode.AUTO : requestedMode;
    }
}
