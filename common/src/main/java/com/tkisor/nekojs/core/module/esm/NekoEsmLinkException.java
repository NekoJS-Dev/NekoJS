package com.tkisor.nekojs.core.module.esm;

import java.io.IOException;

public final class NekoEsmLinkException extends IOException {
    private static final long serialVersionUID = 1L;

    // The diagnostic payload is read-only at runtime and the exception is never serialized across
    // processes; the field stays non-transient so serialization does not silently drop the data
    @SuppressWarnings("serial")
    private final NekoEsmDiagnostic diagnostic;

    public NekoEsmLinkException(NekoEsmDiagnostic diagnostic) {
        super(diagnostic.toString());
        this.diagnostic = diagnostic;
    }

    public NekoEsmDiagnostic diagnostic() {
        return diagnostic;
    }
}
