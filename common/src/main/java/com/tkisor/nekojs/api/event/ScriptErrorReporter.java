package com.tkisor.nekojs.api.event;

import com.tkisor.nekojs.script.ScriptType;

/**
 * Static accessor for script error reporting, breaking the api→core dependency
 * that {@code EventBusJS} previously had on {@code DefaultErrorTracker}.
 * <p>
 * Set once during bootstrap; the default no-op implementation silently discards
 * errors until the real reporter is installed.
 */
public final class ScriptErrorReporter {
    private static volatile Reporter instance = Reporter.NOOP;

    private ScriptErrorReporter() {}

    public interface Reporter {
        void recordCallbackError(ScriptType type, String callbackKind, Throwable throwable);

        Reporter NOOP = (type, kind, throwable) -> {};
    }

    public static void set(Reporter reporter) {
        instance = reporter == null ? Reporter.NOOP : reporter;
    }

    public static void recordCallbackError(ScriptType type, String callbackKind, Throwable throwable) {
        instance.recordCallbackError(type, callbackKind, throwable);
    }
}
