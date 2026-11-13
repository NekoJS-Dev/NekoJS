package com.tkisor.nekojs.core.lifecycle;

import com.tkisor.nekojs.api.ScriptType;

/**
 * Structured failure result of a reload, as exposed to callers.
 *
 * <p>The visible fields are generation (sequence number of the failed candidate), phase (where it
 * failed), sourceLocation (location of the failed script; null for a failure not tied to one),
 * owner (owner id of the failed environment) and domain (subsystem the failure belongs to). A
 * failure result carries no repair instructions and exposes no internal lock or private object:
 * consumers read the record fields and the {@link #describe()} text only.
 *
 * @param generation     Sequence number of the failed candidate (monotonic; 0 is the first environment)
 * @param phase          Stage the failure happened in
 * @param sourceLocation Failed script location ({@code server_scripts/foo.js} style); null for a stage-level failure
 * @param owner          Owner id of the failed environment (e.g. {@code ScriptManager[SERVER]})
 * @param domain         Subsystem the failure belongs to (e.g. {@code candidate-context} / {@code script-execution})
 * @param error          Original exception
 */
public record ReloadFailureReport(
        ScriptType type,
        long generation,
        ReloadPhase phase,
        String sourceLocation,
        String owner,
        String domain,
        Throwable error
) {
    public ReloadFailureReport {
        java.util.Objects.requireNonNull(type, "type");
        java.util.Objects.requireNonNull(phase, "phase");
        java.util.Objects.requireNonNull(owner, "owner");
        java.util.Objects.requireNonNull(domain, "domain");
    }

    /** One-line structured description for commands and logs. Contains no repair instructions. */
    public String describe() {
        return "reload failed: type=" + type.name
                + " generation=" + generation
                + " phase=" + phase
                + (sourceLocation == null ? "" : " source=" + sourceLocation)
                + " owner=" + owner
                + " domain=" + domain
                + " error=" + (error == null ? "unknown" : RuntimeCommandResultFormatter.errorMessage(error));
    }
}
