package com.tkisor.nekojs.core.module;

import com.tkisor.nekojs.core.compiler.NekoModuleMode;

/** Language and requested module mode identity shared by preparation and cache stamps. */
record NekoModuleIdentity(String languageId, NekoModuleMode requestedMode) {
    public NekoModuleIdentity {
        languageId = languageId == null || languageId.isBlank() ? "unknown" : languageId;
        requestedMode = requestedMode == null ? NekoModuleMode.AUTO : requestedMode;
    }
}
