package com.tkisor.nekojs.core.error;

import com.tkisor.nekojs.core.lifecycle.NekoReloadException;
import com.tkisor.nekojs.core.module.NekoModuleError;
import com.tkisor.nekojs.core.module.esm.NekoEsmDiagnostic;
import com.tkisor.nekojs.core.module.esm.NekoEsmLinkException;
import graal.graalvm.polyglot.PolyglotException;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/**
 * Phase/owner classification of a raw failure into the frozen diagnostic record vocabulary
 * (ticket 30). Classification walks the exception cause chain once and is deterministic for
 * the same input; it never reads mutable runtime state.
 *
 * <p>Priority: staged {@link NekoModuleError} (the pipeline's own attribution) wins over
 * raw polyglot flags; {@link NekoEsmLinkException} and {@link NekoReloadException} are
 * recognized both bare and wrapped.
 */
final class ScriptDiagnostics {
    /** Bound for the record's {@code message} text. */
    static final int MESSAGE_BOUND = 400;
    /** Bound for the record's {@code cause} summary text. */
    static final int CAUSE_BOUND = 240;

    private ScriptDiagnostics() {}

    /** Classified attribution fields extracted from a raw failure. */
    record Classification(
            DiagnosticPhase phase,
            String owner,
            String stagedSourcePath,
            String moduleIdentity,
            int stagedLine,
            int stagedColumn
    ) {}

    static Classification classify(Throwable raw, String callbackKind) {
        Set<Throwable> seen = new HashSet<>();
        for (Throwable current = raw; current != null && seen.add(current); current = current.getCause()) {
            if (current instanceof NekoModuleError staged) {
                return fromStaged(staged);
            }
            if (current instanceof NekoEsmLinkException link) {
                return fromLink(link);
            }
            if (current instanceof NekoReloadException reload) {
                return new Classification(DiagnosticPhase.RELOAD_CANCEL,
                        ScriptDiagnosticRecord.OWNER_RUNTIME_RELOAD,
                        blankToNull(reload.report().sourceLocation()),
                        blankToNull(reload.report().domain()), -1, -1);
            }
        }

        PolyglotException polyglot = findPolyglot(raw);
        if (polyglot != null) {
            if (polyglot.isCancelled() || polyglot.isInterrupted() || polyglot.isResourceExhausted()) {
                return new Classification(DiagnosticPhase.WATCHDOG,
                        ScriptDiagnosticRecord.OWNER_WATCHDOG, null, null, -1, -1);
            }
            if (polyglot.isSyntaxError()) {
                return new Classification(DiagnosticPhase.PREPARE, NekoModuleError.OWNER_PREPARATION,
                        null, null, -1, -1);
            }
            return new Classification(DiagnosticPhase.EXECUTION, NekoModuleError.OWNER_EXECUTION,
                    null, null, -1, -1);
        }

        // Host-side preflight validators run at load time next to preparation; their failures
        // belong to the prepare phase, not to guest execution.
        if (callbackKind != null && callbackKind.contains("preflight")) {
            return new Classification(DiagnosticPhase.PREPARE, NekoModuleError.OWNER_PREPARATION,
                    null, null, -1, -1);
        }
        return new Classification(DiagnosticPhase.EXECUTION, NekoModuleError.OWNER_EXECUTION,
                null, null, -1, -1);
    }

    private static Classification fromStaged(NekoModuleError staged) {
        DiagnosticPhase phase = switch (staged.stage()) {
            case PREPARE -> NekoModuleError.OWNER_PACK_TRUST.equals(staged.owner())
                    ? DiagnosticPhase.TRUST
                    : DiagnosticPhase.PREPARE;
            case RESOLVE, LINK -> DiagnosticPhase.RESOLVE_LINK;
            case CACHE -> DiagnosticPhase.CACHE;
            case EXECUTE -> DiagnosticPhase.EXECUTION;
        };
        return new Classification(phase, staged.owner(), blankToNull(staged.sourcePath()),
                blankToNull(staged.moduleId()), staged.sourceLine(), staged.sourceColumn());
    }

    private static Classification fromLink(NekoEsmLinkException link) {
        NekoEsmDiagnostic diagnostic = link.diagnostic();
        if (diagnostic == null) {
            return new Classification(DiagnosticPhase.RESOLVE_LINK, NekoModuleError.OWNER_RESOLUTION_CACHE,
                    null, null, -1, -1);
        }
        Path file = diagnostic.file();
        return new Classification(DiagnosticPhase.RESOLVE_LINK, NekoModuleError.OWNER_RESOLUTION_CACHE,
                null, file == null ? null : file.toString().replace('\\', '/'),
                diagnostic.line(), diagnostic.column());
    }

    /**
     * Bounded single-line cause summary: exception class + message, no stack frames and no
     * private exception identity. Input can be null (recorded as "unknown").
     */
    static String boundedCause(Throwable throwable) {
        if (throwable == null) {
            return "unknown";
        }
        Throwable primary = primaryCause(throwable);
        String text = primary.getClass().getSimpleName()
                + (primary.getMessage() == null || primary.getMessage().isBlank()
                        ? ""
                        : ": " + primary.getMessage().strip());
        return boundedText(text, CAUSE_BOUND);
    }

    static String boundedText(String text, int bound) {
        if (text == null || text.isBlank()) {
            return "unknown";
        }
        String singleLine = text.strip().replace('\n', ' ').replace('\r', ' ');
        if (singleLine.length() <= bound) {
            return singleLine;
        }
        return singleLine.substring(0, bound - 3) + "...";
    }

    private static Throwable primaryCause(Throwable throwable) {
        Throwable best = throwable;
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (current.getMessage() != null && !current.getMessage().isBlank()) {
                best = current;
            }
            if (current.getCause() == null || current.getCause() == current) {
                break;
            }
        }
        return best;
    }

    private static PolyglotException findPolyglot(Throwable throwable) {
        Set<Throwable> seen = new HashSet<>();
        for (Throwable current = throwable; current != null && seen.add(current); current = current.getCause()) {
            if (current instanceof PolyglotException polyglotException) {
                return polyglotException;
            }
        }
        return null;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
