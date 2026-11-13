package com.tkisor.nekojs.core.lifecycle;

/**
 * A transactional reload failed. Carries the structured {@link ReloadFailureReport} (generation,
 * phase, source location, owner, domain).
 *
 * <p>Thrown by the transactional reload path in {@code ScriptManager} and passed through
 * {@code NekoRuntimeRoot.reload} to commands and platform callers. The message text matches
 * {@link ReloadFailureReport#describe()} and contains no repair guidance.
 */
public final class NekoReloadException extends RuntimeException {
    private final ReloadFailureReport report;

    public NekoReloadException(ReloadFailureReport report) {
        super(report.describe(), report.error());
        this.report = report;
    }

    /** Structured failure result; it carries the same information as {@link #getMessage()}. */
    public ReloadFailureReport report() {
        return report;
    }
}
