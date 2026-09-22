package com.tkisor.nekojs.core.error;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.core.lifecycle.NekoReloadException;
import com.tkisor.nekojs.core.lifecycle.ReloadFailureReport;
import com.tkisor.nekojs.network.ErrorSummaryDTO;

import java.util.Objects;

/**
 * Frozen diagnostic record of one script failure (ticket 30).
 *
 * <p>The record is created once, when the failure enters the {@link ErrorTracker}, and never
 * mutated afterwards: attribution fields (id, phase, owner, generation, source, module
 * identity, cache revision, cause) are frozen at record time so later generation switches can
 * never re-attribute old history. All user-visible projections — the per-type log history,
 * the {@link ErrorSummaryDTO dashboard packet}, the external-IDE-workspace open-action seam
 * and the user report — are derived from this single record instead of re-deriving fields.
 *
 * <p>Contract notes:
 * <ul>
 *   <li>{@code generation} is the ScriptManager generation the error was recorded under;
 *       {@code -1} when no generation was published for the environment (bare test setups);</li>
 *   <li>{@code recordedDuringCandidate} is true when the error was observed on a candidate
 *       generation that had not committed yet. After a successful commit the number stays the
 *       committed generation; a failed candidate discards its records entirely;</li>
 *   <li>{@code sourcePath} is the authored/mapped original source location when one is known
 *       (source-mapped for transpiled languages), {@code null} otherwise. It is not a
 *       UI display path: fallback pseudo paths never appear here;</li>
 *   <li>{@code moduleIdentity} keeps the executing module identity (generated module path,
 *       import specifier or ESM file) next to the mapped source;</li>
 *   <li>{@code line}/{@code column} are the original authored positions after source mapping,
 *       {@code -1} when unknown;</li>
 *   <li>{@code message}/{@code cause} are bounded, single-line texts (see
 *       {@link ScriptDiagnostics#boundedCause(Throwable)}); no stack traces, no private
 *       exception object identity.</li>
 * </ul>
 *
 * @param errorId                stable error id ({@code ScriptId#toString()})
 * @param scriptType             owning script type
 * @param phase                  diagnostic phase of the failure
 * @param owner                  owning pipeline module name (e.g. {@code Script Preparation})
 * @param generation             generation the error was recorded under ({@code -1} unknown)
 * @param recordedDuringCandidate whether the error was observed on an uncommitted candidate
 * @param sourcePath             authored/mapped original source path, {@code null} when unknown
 * @param line                   original source line, {@code -1} when unknown
 * @param column                 original source column, {@code -1} when unknown
 * @param moduleIdentity         executing module identity, {@code null} when unknown
 * @param cacheRevision          cache key of the prepared module behind the mapping, {@code null} when not applicable
 * @param message                concise error message
 * @param cause                  bounded cause summary (class + message), never blank
 */
public record ScriptDiagnosticRecord(
        String errorId,
        ScriptType scriptType,
        DiagnosticPhase phase,
        String owner,
        long generation,
        boolean recordedDuringCandidate,
        String sourcePath,
        int line,
        int column,
        String moduleIdentity,
        String cacheRevision,
        String message,
        String cause
) {
    /** Owner name for watchdog/resource-limit terminations. */
    public static final String OWNER_WATCHDOG = "Runaway Watchdog";
    /** Owner name for transactional reload failures and candidate cancellation. */
    public static final String OWNER_RUNTIME_RELOAD = "Runtime Reload";

    public ScriptDiagnosticRecord {
        Objects.requireNonNull(errorId, "errorId");
        Objects.requireNonNull(scriptType, "scriptType");
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(owner, "owner");
        message = message == null || message.isBlank() ? "Unknown error" : message;
        cause = cause == null || cause.isBlank() ? "unknown" : cause;
        sourcePath = sourcePath == null || sourcePath.isBlank() ? null : sourcePath;
        moduleIdentity = moduleIdentity == null || moduleIdentity.isBlank() ? null : moduleIdentity;
        cacheRevision = cacheRevision == null || cacheRevision.isBlank() ? null : cacheRevision;
    }

    /**
     * Project a transactional reload failure into the same frozen record shape. Reload
     * failures stay exceptions for the command/log surface; this projection only feeds the
     * unified record consumers (user report, telemetry, workspace seam).
     */
    public static ScriptDiagnosticRecord ofReloadFailure(NekoReloadException failure) {
        Objects.requireNonNull(failure, "failure");
        ReloadFailureReport report = failure.report();
        return new ScriptDiagnosticRecord(
                "nekojs:reload/" + report.type().name() + "/" + report.generation() + "/" + report.phase(),
                report.type(),
                DiagnosticPhase.RELOAD_CANCEL,
                OWNER_RUNTIME_RELOAD,
                report.generation(),
                true,
                blankToNull(report.sourceLocation()),
                -1,
                -1,
                blankToNull(report.domain()),
                null,
                boundedMessage(report.error() == null ? failure.getMessage() : report.error().toString()),
                ScriptDiagnostics.boundedCause(report.error() == null ? failure : report.error()));
    }

    /**
     * The dashboard packet projection. The wire shape (six fields) is unchanged; the caller
     * supplies the legacy display path and the frozen detail snapshot so this projection is
     * the single place where the packet fields are assembled.
     */
    public ErrorSummaryDTO toErrorSummary(int occurrenceCount, String displayPath, String fullDetails) {
        return new ErrorSummaryDTO(
                errorId,
                displayPath == null || displayPath.isBlank() ? "Unknown location" : displayPath,
                line,
                occurrenceCount,
                message,
                fullDetails == null ? "" : fullDetails);
    }

    /**
     * The external-IDE-workspace open-action projection (non-GUI seam, ticket 27 consumes).
     * Returns {@code null} when no locatable authored source exists.
     */
    public DiagnosticOpenAction openAction() {
        if (sourcePath == null || line < 0) {
            return null;
        }
        return new DiagnosticOpenAction(DiagnosticOpenAction.ACTION_OPEN_SOURCE,
                scriptType, owner, generation, sourcePath, line, column);
    }

    /** Stable English attribution line for logs and the user report; no repair hints. */
    public String describe() {
        StringBuilder text = new StringBuilder("script-diagnostic id=").append(errorId)
                .append(" type=").append(scriptType.name)
                .append(" phase=").append(phase)
                .append(" owner=").append(owner)
                .append(" generation=").append(generation)
                .append(" candidate=").append(recordedDuringCandidate);
        if (sourcePath != null) {
            text.append(" source=").append(sourcePath);
            if (line > 0) {
                text.append(':').append(line);
                if (column > 0) {
                    text.append(':').append(column);
                }
            }
        }
        if (moduleIdentity != null) {
            text.append(" module=").append(moduleIdentity);
        }
        if (cacheRevision != null) {
            text.append(" cacheRevision=").append(cacheRevision);
        }
        text.append(" cause=").append(cause);
        return text.toString();
    }

    static String boundedMessage(String message) {
        return ScriptDiagnostics.boundedText(message, ScriptDiagnostics.MESSAGE_BOUND);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
