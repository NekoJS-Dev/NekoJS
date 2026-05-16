package com.tkisor.nekojs.core.module.esm;

import java.io.IOException;

public final class NekoEsmLinkException extends IOException {
    private final NekoEsmDiagnostic diagnostic;

    public NekoEsmLinkException(NekoEsmDiagnostic diagnostic) {
        super(diagnostic.toString());
        this.diagnostic = diagnostic;
    }

    public NekoEsmDiagnostic diagnostic() {
        return diagnostic;
    }
}
