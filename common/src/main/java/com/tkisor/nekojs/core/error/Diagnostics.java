package com.tkisor.nekojs.core.error;

import com.tkisor.nekojs.NekoJS;

/**
 * Host-side diagnostics channel: it turns catch-and-continue failures scattered around the code
 * from DEBUG or silence into visible reports tagged with a scope.
 *
 * <p>Division of work with {@link ErrorTracker}: ErrorTracker records only errors attributed to a
 * script (entry execution, event/timer callbacks) and feeds the in-game error panel; Diagnostics
 * covers host-side engineering failures where "something quietly stopped working" — a partial
 * directory discovery, a preflight schema downgrade, a file dropped during sync, a probe/editor
 * config that was not written. By default it logs WARN/ERROR through the root logger as
 * {@code [scope] message}; a host can call {@link #install(Sink)} to swap in its own sink.
 *
 * <p>Calling rules: report never throws (a failure inside the channel itself falls back to the
 * root logger), so it can be used inside any catch block. Use a stable lower-case scope string
 * (such as {@code script-discovery} or {@code script-sync}) so users can grep it and a host can
 * filter or throttle by scope.
 */
public final class Diagnostics {

    public enum Severity { WARN, ERROR }

    /** One diagnostic report: scope identifies the subsystem, message is meant for the user,
     * throwable may be null. */
    public record Event(String scope, Severity severity, String message, Throwable throwable) {}

    @FunctionalInterface
    public interface Sink {
        void accept(Event event);
    }

    private static volatile Sink sink = Diagnostics::logToRootLogger;

    private Diagnostics() {}

    /** Swaps in the host's sink; passing null restores the default root logger behavior. */
    public static void install(Sink newSink) {
        sink = newSink == null ? Diagnostics::logToRootLogger : newSink;
    }

    public static void report(String scope, Severity severity, String message) {
        report(scope, severity, message, null);
    }

    public static void report(String scope, Severity severity, String message, Throwable throwable) {
        if (scope == null || scope.isBlank()) {
            scope = "nekojs";
        }
        Event event = new Event(scope, severity, message, throwable);
        try {
            sink.accept(event);
        } catch (Throwable suppressed) {
            // The diagnostics channel never throws at its caller; a failing sink falls back to the root logger.
            logToRootLogger(event);
        }
    }

    private static void logToRootLogger(Event event) {
        String line = "[" + event.scope() + "] " + event.message();
        if (event.severity() == Severity.ERROR) {
            NekoJS.LOGGER.error(line, event.throwable());
        } else {
            NekoJS.LOGGER.warn(line, event.throwable());
        }
    }
}
