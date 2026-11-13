package com.tkisor.nekojs.api.event;

import com.tkisor.nekojs.api.ScriptType;
import graal.graalvm.polyglot.PolyglotException;
import graal.graalvm.polyglot.Context;

/**
 * Static accessor for script error reporting. It breaks the api to core dependency that
 * {@code EventBusJS} had on {@code DefaultErrorTracker}.
 * <p>
 * Set once during bootstrap by the shared assembly function {@code NekoRuntimeAssembly}, which wraps
 * the root's ErrorTracker. Until the real reporter is installed, the default no-op implementation
 * discards errors silently.
 *
 * <p>Ownership: this class is a process-wide static reporting front for the root-owned ErrorTracker.
 * The assembly sequence sets it once, so it does not become a second runtime owner. It lets static
 * contexts that cannot be injected, such as {@code RecipeManagerMixin}, read the error state.
 */
public final class ScriptErrorReporter {
    private static volatile Reporter instance = Reporter.NOOP;

    private ScriptErrorReporter() {}

    public interface Reporter {
        void recordCallbackError(ScriptType type, String callbackKind, Throwable throwable);

        default void recordCallbackError(Object context, ScriptType type, String callbackKind, Throwable throwable) {
            recordCallbackError(type, callbackKind, throwable);
        }

        /**
         * Reports a Graal exception thrown by an event callback (recipe scripts and the like); the
         * semantics match {@code ErrorTracker#recordEventError}.
         * <p>Threading: callable from any thread (recipe handling runs on the server thread, and a mixin
         * hit point may run on the render thread), so an implementation must be thread-safe itself.
         * Reload: {@code set} runs once per process, during assembly, and a reload does not rebuild the
         * reporter, so an implementation must not assume a new instance per reload.</p>
         */
        default void recordEventError(ScriptType type, PolyglotException error) {
            recordCallbackError(type, "event", error);
        }

        default void recordEventError(Object context, ScriptType type, PolyglotException error) {
            recordCallbackError(context, type, "event", error);
        }

        /**
         * Whether any error has been recorded; the semantics match {@code ErrorTracker#hasErrors}.
         * The threading rule is the same as {@link #recordEventError}.
         */
        default boolean hasErrors() {
            return false;
        }

        /**
         * Number of errors; the semantics match the size of the ErrorTracker error set and
         * {@code ErrorSnapshot#count}. The threading rule is the same as {@link #recordEventError}.
         */
        default int errorCount() {
            return 0;
        }

        Reporter NOOP = (type, kind, throwable) -> {};
    }

    public static void set(Reporter reporter) {
        instance = reporter == null ? Reporter.NOOP : reporter;
    }

    public static void recordCallbackError(ScriptType type, String callbackKind, Throwable throwable) {
        instance.recordCallbackError(type, callbackKind, throwable);
    }

    static void recordCallbackError(Context context, ScriptType type, String callbackKind, Throwable throwable) {
        instance.recordCallbackError((Object) context, type, callbackKind, throwable);
    }

    /** Internal bridge used without exposing Context in preparation-layer signatures. */
    static void recordCallbackError(Object context, ScriptType type, String callbackKind, Throwable throwable) {
        if (context instanceof Context polyglotContext) {
            recordCallbackError(polyglotContext, type, callbackKind, throwable);
        } else {
            recordCallbackError(type, callbackKind, throwable);
        }
    }

    /**
     * Reports a Graal exception from an event callback through the root tracker. Static contexts use
     * this in place of an injected tracker.
     */
    public static void recordEventError(ScriptType type, PolyglotException error) {
        instance.recordEventError(type, error);
    }

    static void recordEventError(Context context, ScriptType type, PolyglotException error) {
        instance.recordEventError((Object) context, type, error);
    }

    public static boolean hasErrors() {
        return instance.hasErrors();
    }

    public static int errorCount() {
        return instance.errorCount();
    }
}
