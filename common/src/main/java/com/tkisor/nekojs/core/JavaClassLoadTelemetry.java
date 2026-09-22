package com.tkisor.nekojs.core;

import com.tkisor.nekojs.core.JavaClassLoadTelemetrySink;
import com.tkisor.nekojs.api.ScriptType;

/**
 * Java class-load telemetry scope (ticket 07 observability, ticket 30 privacy bounds).
 *
 * <p>Disable/degrade semantics: telemetry is opt-in — nothing is recorded until a sink is
 * installed via {@link #setSink}, and installing {@code null} (or never installing) disables
 * collection entirely. Recording itself is a thread-local scope plus one sink call, so a
 * degrading or no-op sink has no observable effect on script execution.
 */
public final class JavaClassLoadTelemetry {
    private static volatile JavaClassLoadTelemetrySink sink = JavaClassLoadTelemetrySink.EMPTY;
    private static final ThreadLocal<ContextInfo> CURRENT = new ThreadLocal<>();

    public JavaClassLoadTelemetry() {}

    public static void setSink(JavaClassLoadTelemetrySink newSink) {
        sink = newSink == null ? JavaClassLoadTelemetrySink.EMPTY : newSink;
    }

    public static boolean isEnabled() {
        return sink != JavaClassLoadTelemetrySink.EMPTY;
    }

    public static void enter(ScriptType scriptType, String scriptId) {
        CURRENT.set(new ContextInfo(scriptType, scriptId));
    }

    public static void exit() {
        CURRENT.remove();
    }

    public static void recordAttempt(String className, boolean allowed) {
        ContextInfo info = CURRENT.get();
        if (info != null) {
            sink.recordAttempt(info.scriptType(), info.scriptId(), className, allowed);
        }
    }

    public void recordLoad(String scriptTypeName, String scriptId, String className) {
        ScriptType scriptType = ScriptType.valueOf(scriptTypeName);
        sink.recordLoad(scriptType, scriptId, className);
    }

    private record ContextInfo(ScriptType scriptType, String scriptId) {}
}
