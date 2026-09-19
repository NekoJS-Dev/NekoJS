package com.tkisor.nekojs.core.error;

import com.tkisor.nekojs.api.event.ScriptErrorReporter;
import com.tkisor.nekojs.api.ScriptType;
import graal.graalvm.polyglot.PolyglotException;
import graal.graalvm.polyglot.Context;

/**
 * Adapts an {@link ErrorTracker} into a {@link ScriptErrorReporter.Reporter}, so that callback
 * errors captured by the API layer (runtime listener exceptions in {@code EventBusJS} and
 * registration-time preflight diagnostics from {@code EventCallbackSourceValidator}) flow into
 * the core error tracker and surface in the in-game error dashboard.
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

    @Override
    public void recordCallbackError(Object context, ScriptType type, String callbackKind, Throwable throwable) {
        delegate.recordCallbackError(context instanceof Context polyglotContext ? polyglotContext : null,
                type, callbackKind, throwable);
    }

    @Override
    public void recordEventError(ScriptType type, PolyglotException error) {
        delegate.recordEventError(type, error);
    }

    @Override
    public void recordEventError(Object context, ScriptType type, PolyglotException error) {
        delegate.recordEventError(context instanceof Context polyglotContext ? polyglotContext : null,
                type, error);
    }

    @Override
    public boolean hasErrors() {
        return delegate.hasErrors();
    }

    @Override
    public int errorCount() {
        return delegate.getErrorCount();
    }
}
