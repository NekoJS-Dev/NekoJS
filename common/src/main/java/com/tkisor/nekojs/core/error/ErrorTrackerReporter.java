package com.tkisor.nekojs.core.error;

import com.tkisor.nekojs.api.event.ScriptErrorReporter;
import com.tkisor.nekojs.script.ScriptType;

/**
 * Adapts an {@link ErrorTracker} into a {@link ScriptErrorReporter.Reporter}, so that callback
 * errors captured by the API layer (runtime listener exceptions in {@code EventBusJS} and
 * registration-time preflight diagnostics from {@code EventCallbackSourceValidator}) flow into the
 * core error tracker and surface in the in-game error dashboard.
 *
 * <p>Set once during bootstrap via {@link ScriptErrorReporter#set(Reporter)}; a runtime reload that
 * rebuilds the tracker re-binds a fresh instance, so no explicit {@code set(null)} is required.
 */
public final class ErrorTrackerReporter implements ScriptErrorReporter.Reporter {
    private final ErrorTracker delegate;

    public ErrorTrackerReporter(ErrorTracker delegate) {
        this.delegate = delegate;
    }

    @Override
    public void recordCallbackError(ScriptType type, String callbackKind, Throwable throwable) {
        delegate.recordCallbackError(type, callbackKind, throwable);
    }
}
