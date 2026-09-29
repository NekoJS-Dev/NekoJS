package com.tkisor.nekojs.core.lifecycle;

/** Formats lifecycle results for loader command output without exposing stack traces. */
public final class RuntimeCommandResultFormatter {

    private RuntimeCommandResultFormatter() {}

    public static String reloadResult(NekoRuntimeRoot.ReloadResult result, boolean activeIsolated) {
        if (result.success()) {
            String phase = "generation=" + result.generation() + " phase=" + result.phase();
            if (result.phase() == ReloadPhase.FILE) {
                return "NekoJS " + result.type().name + " script reload completed: " + phase
                        + source(result.sourceLocation());
            }
            if (result.nonTransactional()) {
                return "NekoJS " + result.type().name + " reload completed non-transactionally: " + phase
                        + source(result.sourceLocation())
                        + (result.requiresLoaderRestart()
                                ? "; restart the game/loader for a clean STARTUP state." : ".");
            }
            return "NekoJS " + result.type().name + " reload committed: " + phase;
        }

        String message = "reload failed: type=" + result.type().name
                + " generation=" + result.generation()
                + " phase=" + result.phase()
                + source(result.sourceLocation())
                + " owner=ScriptManager[" + result.type().name + "]"
                + (result.error() == null ? "" : " error=" + errorMessage(result.error()));
        if (activeIsolated) {
            return message + "; active generation remains isolated; explicit full reload is required.";
        }
        return message + (result.phase() == ReloadPhase.FILE
                ? "; active runtime was not switched; use a full reload to reconcile it."
                : "; no active generation was changed.");
    }

    public static String reloadFailure(ReloadFailureReport report, boolean activeIsolated) {
        return report.describe() + (activeIsolated
                ? "; active generation remains isolated; explicit full reload is required."
                : "; candidate was discarded and the active generation remains unchanged.");
    }

    public static String postReloadFailure(NekoRuntimeRoot.ReloadResult result, String stage, Throwable failure) {
        return "NekoJS " + result.type().name + " reload committed (generation=" + result.generation()
                + " phase=" + result.phase() + "); post-reload " + stage + " failed"
                + (failure == null ? "." : ": " + errorMessage(failure));
    }

    public static String testResult(NekoRuntimeRoot.TestRunResult result) {
        if (!result.isConfigured()) {
            return "NekoJS TEST scripts are not configured.";
        }
        return result.isCompleted()
                ? "NekoJS TEST scripts completed."
                : "NekoJS TEST scripts did not complete.";
    }

    private static String source(String sourceLocation) {
        return sourceLocation == null || sourceLocation.isBlank() ? "" : " source=" + sourceLocation;
    }

    /**
     * User-facing error text: the message only. The exception class name stays in logs
     * (callers log the throwable); a null/blank message falls back to the simple class name
     * so the failure line never ends up with an empty reason.
     */
    static String errorMessage(Throwable error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
    }
}
